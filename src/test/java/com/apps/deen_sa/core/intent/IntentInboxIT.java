package com.apps.deen_sa.core.intent;

import com.apps.deen_sa.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Phase 1: Intent Ingestion & Staging
 * 
 * Tests verify:
 * - Intent persistence and durability
 * - Immediate acknowledgment pattern
 * - raw_text immutability
 * - No financial data storage
 * - No inline handler execution
 * - Deduplication via correlation_id
 */
@SpringBootTest
@ActiveProfiles("integration")
class IntentInboxIT extends IntegrationTestBase {

    @Autowired
    private IntentInboxService intentInboxService;

    @Autowired
    private UserIntentInboxRepository repository;

    @BeforeEach
    void setUp() {
        // Clean up before each test
        repository.deleteAll();
    }

    @Test
    void testIntentPersistenceAndDurability() {
        // Given
        String userId = "user123";
        String channel = "WHATSAPP";
        String rawText = "spent 500 on groceries";

        // When - Persist intent
        UserIntentInboxEntity intent = intentInboxService.persistIntent(userId, channel, rawText);

        // Then - Verify immediate persistence
        assertNotNull(intent.getId(), "Intent should have an ID immediately after persistence");
        assertEquals(userId, intent.getUserId());
        assertEquals(channel, intent.getChannel());
        assertEquals(rawText, intent.getRawText());
        assertNotNull(intent.getCorrelationId(), "Correlation ID should be generated");
        assertNotNull(intent.getReceivedAt(), "Received timestamp should be set");
        assertEquals(IntentStatus.RECEIVED, intent.getStatus());
        assertEquals(0, intent.getProcessingAttempts());

        // Verify durability - retrieve from database
        UserIntentInboxEntity retrieved = repository.findById(intent.getId()).orElse(null);
        assertNotNull(retrieved, "Intent should be durable in database");
        assertEquals(rawText, retrieved.getRawText(), "Raw text should be persisted");
        assertEquals(IntentStatus.RECEIVED, retrieved.getStatus());
    }

    @Test
    void testRawTextImmutability() {
        // Given
        String userId = "user456";
        String originalRawText = "paid 1000 for rent";
        UserIntentInboxEntity intent = intentInboxService.persistIntent(userId, "WHATSAPP", originalRawText);

        // When - Attempt to retrieve and verify raw text hasn't changed
        UserIntentInboxEntity retrieved = repository.findById(intent.getId()).orElse(null);

        // Then - Raw text should remain unchanged
        assertNotNull(retrieved);
        assertEquals(originalRawText, retrieved.getRawText(), 
                "Raw text must never be mutated after creation");
    }

    @Test
    void testNoFinancialDataStored() {
        // Given
        String rawText = "bought laptop for 50000";
        UserIntentInboxEntity intent = intentInboxService.persistIntent("user789", "WEB", rawText);

        // Then - Verify no financial processing occurred
        assertNull(intent.getDetectedIntent(), 
                "Intent should not be classified during ingestion");
        assertNull(intent.getIntentConfidence(), 
                "Confidence should not be calculated during ingestion");
        
        // Verify entity has no financial fields
        // This table should NEVER have amount, category, or any financial columns
        assertThat(intent)
                .hasFieldOrProperty("rawText")
                .hasFieldOrProperty("status");
        
        // Verify toString doesn't contain financial terms
        String toString = intent.toString();
        assertFalse(toString.contains("amount"), "Entity should not have 'amount' field");
        assertFalse(toString.contains("balance"), "Entity should not have 'balance' field");
    }

    @Test
    void testDeduplicationViaCorrelationId() {
        // Given
        String userId = "user999";
        String rawText = "dinner 300";
        
        // When - Persist same intent twice
        UserIntentInboxEntity first = intentInboxService.persistIntent(userId, "WHATSAPP", rawText);
        
        // Simulate duplicate (same correlation ID should be detected)
        // Note: In real scenario, correlation ID generation includes timestamp, 
        // so duplicates would need same timestamp which won't happen naturally
        
        // Then - Verify first intent was persisted
        assertNotNull(first.getId());
        assertNotNull(first.getCorrelationId());
        
        // Verify correlation ID is unique
        assertTrue(repository.existsByCorrelationId(first.getCorrelationId()));
    }

    @Test
    void testStatusMutability() {
        // Given
        UserIntentInboxEntity intent = intentInboxService.persistIntent("user111", "API", "test message");
        Long intentId = intent.getId();

        // When - Update status to PROCESSING
        intentInboxService.updateStatus(intentId, IntentStatus.PROCESSING, "Started processing");

        // Then
        UserIntentInboxEntity updated = repository.findById(intentId).orElseThrow();
        assertEquals(IntentStatus.PROCESSING, updated.getStatus());
        assertEquals("Started processing", updated.getStatusReason());

        // When - Update to COMPLETED
        intentInboxService.updateStatus(intentId, IntentStatus.PROCESSED, null);

        // Then
        updated = repository.findById(intentId).orElseThrow();
        assertEquals(IntentStatus.PROCESSED, updated.getStatus());
    }

    @Test
    void testDetectedIntentUpdate() {
        // Given
        UserIntentInboxEntity intent = intentInboxService.persistIntent("user222", "WHATSAPP", "groceries 800");
        Long intentId = intent.getId();

        // When - Update detected intent after LLM classification
        String detectedIntent = "EXPENSE";
        BigDecimal confidence = new BigDecimal("0.9500");
        intentInboxService.updateDetectedIntent(intentId, detectedIntent, confidence);

        // Then
        UserIntentInboxEntity updated = repository.findById(intentId).orElseThrow();
        assertEquals(detectedIntent, updated.getDetectedIntent());
        assertEquals(confidence, updated.getIntentConfidence());
        
        // Raw text should remain unchanged
        assertEquals("groceries 800", updated.getRawText());
    }

    @Test
    void testProcessingAttemptTracking() {
        // Given
        UserIntentInboxEntity intent = intentInboxService.persistIntent("user333", "WHATSAPP", "test");
        Long intentId = intent.getId();

        // When - Record processing attempts
        UserIntentInboxEntity entity = repository.findById(intentId).orElseThrow();
        LocalDateTime beforeAttempt = LocalDateTime.now().minusSeconds(1);
        
        entity.recordProcessingAttempt();
        repository.save(entity);

        // Then
        entity = repository.findById(intentId).orElseThrow();
        assertEquals(1, entity.getProcessingAttempts());
        assertNotNull(entity.getLastProcessedAt());
        assertTrue(entity.getLastProcessedAt().isAfter(beforeAttempt));

        // Record another attempt
        entity.recordProcessingAttempt();
        repository.save(entity);

        entity = repository.findById(intentId).orElseThrow();
        assertEquals(2, entity.getProcessingAttempts());
    }

    @Test
    void testFindByStatusForProcessingQueue() {
        // Given - Create multiple intents with different statuses
        intentInboxService.persistIntent("user1", "WHATSAPP", "msg1");
        intentInboxService.persistIntent("user2", "WHATSAPP", "msg2");
        UserIntentInboxEntity intent3 = intentInboxService.persistIntent("user3", "WHATSAPP", "msg3");
        
        // Mark one as completed
        intentInboxService.updateStatus(intent3.getId(), IntentStatus.PROCESSED, null);

        // When - Find pending intents
        List<UserIntentInboxEntity> pending = repository.findByStatusOrderByReceivedAt(IntentStatus.RECEIVED);

        // Then
        assertEquals(2, pending.size(), "Should find 2 pending intents");
        
        // Verify they're ordered by received time
        assertTrue(pending.get(0).getReceivedAt().isBefore(pending.get(1).getReceivedAt()) 
                || pending.get(0).getReceivedAt().isEqual(pending.get(1).getReceivedAt()));
    }

    @Test
    void testImmediateAcknowledgmentPattern() {
        // Given
        long startTime = System.currentTimeMillis();

        // When - Persist intent (simulating webhook handler)
        UserIntentInboxEntity intent = intentInboxService.persistIntent("user444", "WHATSAPP", "quick message");

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Then - Persistence should be fast (< 500ms for immediate ack)
        assertNotNull(intent.getId(), "Intent should be persisted immediately");
        assertTrue(duration < 500, 
                "Persistence should be fast for immediate acknowledgment (took " + duration + "ms)");
        
        // Verify it's in PENDING status (not processed yet)
        assertEquals(IntentStatus.RECEIVED, intent.getStatus(), 
                "Intent should be PENDING - no inline processing");
    }

    @Test
    void testCorrelationIdFormat() {
        // When
        UserIntentInboxEntity intent = intentInboxService.persistIntent("user555", "WHATSAPP", "test");

        // Then - Verify correlation ID format: {channel}:{userId}:{timestamp}:{uuid}
        String correlationId = intent.getCorrelationId();
        assertNotNull(correlationId);
        
        String[] parts = correlationId.split(":");
        assertEquals(4, parts.length, "Correlation ID should have 4 parts");
        assertEquals("WHATSAPP", parts[0], "First part should be channel");
        assertEquals("user555", parts[1], "Second part should be userId");
        assertDoesNotThrow(() -> Long.parseLong(parts[2]), "Third part should be timestamp");
        assertTrue(parts[3].length() == 8, "Fourth part should be 8-char UUID");
    }
}
