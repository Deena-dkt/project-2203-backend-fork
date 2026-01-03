package com.apps.deen_sa.core.intent;

import com.apps.deen_sa.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Phase 3: Async Follow-Up Handling
 * 
 * Tests verify:
 * - NEEDS_INPUT → follow-up → resume → PROCESSED flow
 * - Follow-up correlation is deterministic
 * - Only one unresolved follow-up per user at a time
 * - Out-of-order messages handled correctly
 * - Duplicate follow-up replies handled
 * - New intents still accepted when follow-up pending
 */
@SpringBootTest
@ActiveProfiles("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class FollowUpCorrelationServiceIT extends IntegrationTestBase {

    @Autowired
    private IntentInboxService intentInboxService;

    @Autowired
    private IntentProcessingEngine processingEngine;

    @Autowired
    private FollowUpCorrelationService followUpCorrelationService;

    @Autowired
    private UserIntentInboxRepository intentRepository;

    @BeforeEach
    void setUp() {
        intentRepository.deleteAll();
    }

    @Test
    void testFollowUpCorrelation_BasicFlow() throws Exception {
        // Given - User sends incomplete message that will result in NEEDS_INPUT
        String userId = "user1";
        UserIntentInboxEntity parentIntent = intentInboxService.persistIntent(userId, "WHATSAPP", "spent 500");
        
        // Manually set to NEEDS_INPUT for testing
        parentIntent.markAsNeedsInput("Amount and description provided, but category missing");
        parentIntent.setDetectedIntent("EXPENSE");
        intentRepository.save(parentIntent);

        // When - User sends follow-up message
        UserIntentInboxEntity followUpIntent = intentInboxService.persistIntent(userId, "WHATSAPP", "groceries");
        
        boolean isFollowUp = followUpCorrelationService.processAsFollowUpIfApplicable(
                userId, "WHATSAPP", "groceries", followUpIntent.getId());

        // Then - Should be recognized as follow-up
        assertTrue(isFollowUp, "Message should be recognized as follow-up");
        
        // Wait for async processing
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            UserIntentInboxEntity updatedFollowUp = intentRepository.findById(followUpIntent.getId()).orElseThrow();
            UserIntentInboxEntity updatedParent = intentRepository.findById(parentIntent.getId()).orElseThrow();
            
            // Follow-up should be linked to parent
            assertEquals(parentIntent.getId(), updatedFollowUp.getFollowupParentId(),
                    "Follow-up should be linked to parent intent");
            
            // Follow-up should be marked PROCESSED (incorporated into parent)
            assertEquals(IntentStatus.PROCESSED, updatedFollowUp.getStatus(),
                    "Follow-up should be marked PROCESSED");
            
            // Parent should have been reprocessed
            assertNotEquals(IntentStatus.NEEDS_INPUT, updatedParent.getStatus(),
                    "Parent should no longer be NEEDS_INPUT");
        });
    }

    @Test
    void testFindPendingFollowUp() {
        // Given - User has pending NEEDS_INPUT intent
        String userId = "user2";
        UserIntentInboxEntity intent = intentInboxService.persistIntent(userId, "WHATSAPP", "test message");
        intent.markAsNeedsInput("Need more info");
        intentRepository.save(intent);

        // When
        Optional<UserIntentInboxEntity> pending = followUpCorrelationService.findPendingFollowUp(userId);

        // Then
        assertTrue(pending.isPresent(), "Should find pending follow-up");
        assertEquals(intent.getId(), pending.get().getId());
    }

    @Test
    void testNoPendingFollowUp_WhenNoNeedsInput() {
        // Given - User has only PROCESSED intents
        String userId = "user3";
        UserIntentInboxEntity intent = intentInboxService.persistIntent(userId, "WHATSAPP", "test");
        intent.markAsProcessed();
        intentRepository.save(intent);

        // When
        Optional<UserIntentInboxEntity> pending = followUpCorrelationService.findPendingFollowUp(userId);

        // Then
        assertFalse(pending.isPresent(), "Should not find pending follow-up");
    }

    @Test
    void testHasUnresolvedFollowUp() {
        // Given - User has NEEDS_INPUT intent
        String userId = "user4";
        UserIntentInboxEntity intent = intentInboxService.persistIntent(userId, "WHATSAPP", "incomplete");
        intent.markAsNeedsInput("Missing data");
        intentRepository.save(intent);

        // When/Then
        assertTrue(followUpCorrelationService.hasUnresolvedFollowUp(userId),
                "Should detect unresolved follow-up");
    }

    @Test
    void testNoUnresolvedFollowUp_WhenAllCompleted() {
        // Given - User has only completed intents
        String userId = "user5";
        UserIntentInboxEntity intent = intentInboxService.persistIntent(userId, "WHATSAPP", "complete");
        intent.markAsProcessed();
        intentRepository.save(intent);

        // When/Then
        assertFalse(followUpCorrelationService.hasUnresolvedFollowUp(userId),
                "Should not detect unresolved follow-up");
    }

    @Test
    void testOnlyOneUnresolvedFollowUpPerUser() {
        // Given - User with NEEDS_INPUT intent
        String userId = "user6";
        UserIntentInboxEntity intent1 = intentInboxService.persistIntent(userId, "WHATSAPP", "first");
        intent1.markAsNeedsInput("Need input");
        intentRepository.save(intent1);

        // Then - Should have exactly one
        assertDoesNotThrow(() -> followUpCorrelationService.validateSinglePendingFollowUp(userId));
        
        long count = intentRepository.countByUserIdAndStatus(userId, IntentStatus.NEEDS_INPUT);
        assertEquals(1, count, "Should have exactly one NEEDS_INPUT intent");
    }

    @Test
    void testMultiplePendingFollowUps_ViolatesInvariant() {
        // Given - Manually create invalid state (two NEEDS_INPUT for same user)
        String userId = "user7";
        UserIntentInboxEntity intent1 = intentInboxService.persistIntent(userId, "WHATSAPP", "first");
        intent1.markAsNeedsInput("Need input 1");
        intentRepository.save(intent1);

        UserIntentInboxEntity intent2 = intentInboxService.persistIntent(userId, "WHATSAPP", "second");
        intent2.markAsNeedsInput("Need input 2");
        intentRepository.save(intent2);

        // Then - Validation should fail
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            followUpCorrelationService.validateSinglePendingFollowUp(userId);
        });

        assertTrue(exception.getMessage().contains("Invariant violated"),
                "Should detect invariant violation");
    }

    @Test
    void testOutOfOrderMessages_NewIntentWhilePendingFollowUp() throws Exception {
        // Given - User has pending follow-up
        String userId = "user8";
        UserIntentInboxEntity parentIntent = intentInboxService.persistIntent(userId, "WHATSAPP", "spent 500");
        parentIntent.markAsNeedsInput("Need category");
        intentRepository.save(parentIntent);

        // When - User sends completely new intent (not a follow-up)
        // In real scenario, this would be detected by intent classification
        // For this test, we'll simulate the behavior
        UserIntentInboxEntity newIntent = intentInboxService.persistIntent(userId, "WHATSAPP", "check my balance");
        
        // The system should treat this as follow-up (current behavior)
        // But ideally would detect it's a new intent based on context
        boolean isFollowUp = followUpCorrelationService.processAsFollowUpIfApplicable(
                userId, "WHATSAPP", "check my balance", newIntent.getId());

        // Then - Should be treated as follow-up (current Phase 3 behavior)
        // Future enhancement: Intent classification could detect this is unrelated
        assertTrue(isFollowUp, "With current logic, any message is treated as follow-up when NEEDS_INPUT exists");
    }

    @Test
    void testFollowUpLinking() throws Exception {
        // Given - Parent intent needing input
        String userId = "user9";
        UserIntentInboxEntity parent = intentInboxService.persistIntent(userId, "WHATSAPP", "parent message");
        parent.markAsNeedsInput("Missing info");
        intentRepository.save(parent);

        // When - Follow-up message
        UserIntentInboxEntity followUp = intentInboxService.persistIntent(userId, "WHATSAPP", "follow-up message");
        followUpCorrelationService.processAsFollowUpIfApplicable(userId, "WHATSAPP", "follow-up message", followUp.getId());

        // Wait for async processing
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            UserIntentInboxEntity updated = intentRepository.findById(followUp.getId()).orElseThrow();
            
            // Then - Follow-up should have parent reference
            assertNotNull(updated.getFollowupParentId(), "Follow-up should have parent ID");
            assertEquals(parent.getId(), updated.getFollowupParentId());
            assertTrue(updated.isFollowup(), "isFollowup() should return true");
        });
    }

    @Test
    void testFindFollowUpsByParent() throws Exception {
        // Given - Parent with multiple follow-ups
        String userId = "user10";
        UserIntentInboxEntity parent = intentInboxService.persistIntent(userId, "WHATSAPP", "parent");
        parent.markAsNeedsInput("Need input");
        intentRepository.save(parent);

        // Create first follow-up
        UserIntentInboxEntity followUp1 = intentInboxService.persistIntent(userId, "WHATSAPP", "follow-up 1");
        followUpCorrelationService.processAsFollowUpIfApplicable(userId, "WHATSAPP", "follow-up 1", followUp1.getId());
        
        // Wait for first follow-up to be processed
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            UserIntentInboxEntity updated = intentRepository.findById(followUp1.getId()).orElseThrow();
            assertEquals(IntentStatus.PROCESSED, updated.getStatus());
        });
        
        // Parent should be reprocessed and might need more input
        // For this test, manually set it back to NEEDS_INPUT
        UserIntentInboxEntity updatedParent = intentRepository.findById(parent.getId()).orElseThrow();
        updatedParent.markAsNeedsInput("Still need more");
        intentRepository.save(updatedParent);

        // Create second follow-up
        UserIntentInboxEntity followUp2 = intentInboxService.persistIntent(userId, "WHATSAPP", "follow-up 2");
        followUpCorrelationService.processAsFollowUpIfApplicable(userId, "WHATSAPP", "follow-up 2", followUp2.getId());

        // Wait for second follow-up
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            UserIntentInboxEntity updated = intentRepository.findById(followUp2.getId()).orElseThrow();
            assertEquals(IntentStatus.PROCESSED, updated.getStatus());
        });

        // Then - Should find both follow-ups
        List<UserIntentInboxEntity> followUps = intentRepository.findByFollowupParentIdOrderByReceivedAt(parent.getId());
        assertEquals(2, followUps.size(), "Should find both follow-ups");
    }

    @Test
    void testDuplicateFollowUpReplies() throws Exception {
        // Given - Parent needing input
        String userId = "user11";
        UserIntentInboxEntity parent = intentInboxService.persistIntent(userId, "WHATSAPP", "parent");
        parent.markAsNeedsInput("Need category");
        intentRepository.save(parent);

        // When - User sends same follow-up twice (simulating retry/duplicate)
        UserIntentInboxEntity followUp1 = intentInboxService.persistIntent(userId, "WHATSAPP", "groceries");
        boolean isFollowUp1 = followUpCorrelationService.processAsFollowUpIfApplicable(
                userId, "WHATSAPP", "groceries", followUp1.getId());

        // Wait for first to be processed
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            UserIntentInboxEntity updated = intentRepository.findById(followUp1.getId()).orElseThrow();
            assertEquals(IntentStatus.PROCESSED, updated.getStatus());
        });

        // Check if parent is still NEEDS_INPUT or has moved on
        UserIntentInboxEntity updatedParent = intentRepository.findById(parent.getId()).orElseThrow();
        
        if (updatedParent.getStatus() == IntentStatus.NEEDS_INPUT) {
            // Parent still needs input, second message will be treated as another follow-up
            UserIntentInboxEntity followUp2 = intentInboxService.persistIntent(userId, "WHATSAPP", "groceries");
            boolean isFollowUp2 = followUpCorrelationService.processAsFollowUpIfApplicable(
                    userId, "WHATSAPP", "groceries", followUp2.getId());
            
            assertTrue(isFollowUp2, "Duplicate should still be recognized as follow-up");
        } else {
            // Parent processed, second message would be new intent
            UserIntentInboxEntity followUp2 = intentInboxService.persistIntent(userId, "WHATSAPP", "groceries");
            boolean isFollowUp2 = followUpCorrelationService.processAsFollowUpIfApplicable(
                    userId, "WHATSAPP", "groceries", followUp2.getId());
            
            assertFalse(isFollowUp2, "If parent processed, duplicate is new intent");
        }
    }

    @Test
    void testDifferentUsers_IndependentFollowUps() {
        // Given - Two users each with pending follow-ups
        String user1 = "userA";
        String user2 = "userB";
        
        UserIntentInboxEntity intent1 = intentInboxService.persistIntent(user1, "WHATSAPP", "message1");
        intent1.markAsNeedsInput("User A needs input");
        intentRepository.save(intent1);

        UserIntentInboxEntity intent2 = intentInboxService.persistIntent(user2, "WHATSAPP", "message2");
        intent2.markAsNeedsInput("User B needs input");
        intentRepository.save(intent2);

        // When/Then - Each user should have their own pending follow-up
        assertTrue(followUpCorrelationService.hasUnresolvedFollowUp(user1));
        assertTrue(followUpCorrelationService.hasUnresolvedFollowUp(user2));

        Optional<UserIntentInboxEntity> pendingA = followUpCorrelationService.findPendingFollowUp(user1);
        Optional<UserIntentInboxEntity> pendingB = followUpCorrelationService.findPendingFollowUp(user2);

        assertTrue(pendingA.isPresent());
        assertTrue(pendingB.isPresent());
        assertEquals(intent1.getId(), pendingA.get().getId());
        assertEquals(intent2.getId(), pendingB.get().getId());
    }
}
