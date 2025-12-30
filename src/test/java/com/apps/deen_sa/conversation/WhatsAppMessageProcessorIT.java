package com.apps.deen_sa.conversation;

import com.apps.deen_sa.IntegrationTestBase;
import com.apps.deen_sa.core.intent.IntentStatus;
import com.apps.deen_sa.core.intent.UserIntentInboxEntity;
import com.apps.deen_sa.core.intent.UserIntentInboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration test for Phase 1: Intent Ingestion & Staging
 * 
 * Verifies the complete flow:
 * 1. Webhook receives message
 * 2. Intent persisted immediately to inbox
 * 3. Immediate acknowledgment (webhook returns)
 * 4. Processing happens asynchronously
 * 5. No financial handlers execute during persistence
 */
@SpringBootTest
@ActiveProfiles("integration")
class WhatsAppMessageProcessorIT extends IntegrationTestBase {

    @Autowired
    private WhatsAppMessageProcessor messageProcessor;

    @Autowired
    private UserIntentInboxRepository intentRepository;

    @BeforeEach
    void setUp() {
        intentRepository.deleteAll();
    }

    @Test
    void testIntentIsPersistedBeforeProcessing() throws Exception {
        // Given
        String userId = "test-user-123";
        String message = "spent 500 on groceries";
        long startTime = System.currentTimeMillis();

        // When - Simulate webhook receiving message
        // Note: This is @Async, so it returns immediately
        messageProcessor.processIncomingMessage(userId, message);

        // Then - Verify intent was persisted quickly (for immediate ack)
        // Wait up to 2 seconds for async persistence to complete
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            List<UserIntentInboxEntity> intents = intentRepository.findByUserIdAndStatusOrderByReceivedAtDesc(
                    userId, IntentStatus.RECEIVED);
            
            // At least one intent should be persisted
            assertTrue(intents.size() >= 1, "Intent should be persisted to inbox");
            
            UserIntentInboxEntity intent = intents.get(0);
            assertEquals(userId, intent.getUserId());
            assertEquals("WHATSAPP", intent.getChannel());
            assertEquals(message, intent.getRawText());
            assertNotNull(intent.getCorrelationId());
            assertNotNull(intent.getReceivedAt());
        });

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Persistence should be fast enough for immediate webhook acknowledgment
        assertTrue(duration < 3000, 
                "Async persistence should complete quickly (took " + duration + "ms)");
    }

    @Test
    void testRawTextPreservedDuringProcessing() throws Exception {
        // Given
        String userId = "test-user-456";
        String originalMessage = "paid credit card bill 10000";

        // When - Process message
        messageProcessor.processIncomingMessage(userId, originalMessage);

        // Then - Wait for persistence and verify raw text is unchanged
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            List<UserIntentInboxEntity> intents = intentRepository.findByUserIdAndStatusOrderByReceivedAtDesc(
                    userId, IntentStatus.RECEIVED);
            
            assertTrue(intents.size() >= 1);
            UserIntentInboxEntity intent = intents.get(0);
            
            // Raw text should be exactly as received
            assertEquals(originalMessage, intent.getRawText(), 
                    "Raw text must never be mutated");
            
            // No financial processing should have occurred during ingestion
            assertNull(intent.getDetectedIntent(), 
                    "Intent classification happens separately from ingestion");
        });
    }

    @Test
    void testMultipleMessagesFromSameUser() throws Exception {
        // Given
        String userId = "test-user-789";
        String message1 = "groceries 300";
        String message2 = "dinner 500";
        String message3 = "fuel 2000";

        // When - Process multiple messages
        messageProcessor.processIncomingMessage(userId, message1);
        messageProcessor.processIncomingMessage(userId, message2);
        messageProcessor.processIncomingMessage(userId, message3);

        // Then - All should be persisted
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            List<UserIntentInboxEntity> intents = intentRepository.findByUserIdAndStatusOrderByReceivedAtDesc(
                    userId, IntentStatus.RECEIVED);
            
            assertTrue(intents.size() >= 3, "All 3 messages should be persisted");
            
            // Verify all messages are preserved
            List<String> rawTexts = intents.stream()
                    .map(UserIntentInboxEntity::getRawText)
                    .toList();
            
            assertTrue(rawTexts.contains(message1));
            assertTrue(rawTexts.contains(message2));
            assertTrue(rawTexts.contains(message3));
            
            // Verify each has unique correlation ID
            List<String> correlationIds = intents.stream()
                    .map(UserIntentInboxEntity::getCorrelationId)
                    .toList();
            
            assertEquals(correlationIds.size(), 
                    correlationIds.stream().distinct().count(),
                    "Each message should have unique correlation ID");
        });
    }

    @Test
    void testImmediateAcknowledgmentPattern() {
        // Given
        String userId = "test-user-999";
        String message = "quick test message";

        // When - Measure time for async call to return
        long startTime = System.currentTimeMillis();
        messageProcessor.processIncomingMessage(userId, message);
        long endTime = System.currentTimeMillis();
        long asyncCallDuration = endTime - startTime;

        // Then - Async call should return immediately (< 100ms)
        assertTrue(asyncCallDuration < 100, 
                "Async method call should return immediately (took " + asyncCallDuration + "ms)");
        
        // Note: Actual processing happens in background
        // Webhook can return 200 OK immediately while processing continues
    }
}
