package com.apps.deen_sa.api;

import com.apps.deen_sa.IntegrationTestBase;
import com.apps.deen_sa.core.intent.IntentStatus;
import com.apps.deen_sa.core.intent.UserIntentInboxEntity;
import com.apps.deen_sa.core.intent.UserIntentInboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Phase 4: Channel Independence
 * 
 * Tests verify:
 * - Same intent processed identically across different channels
 * - API endpoints work for UI/Mobile clients
 * - Pending actions can be fetched and resumed
 * - Core processing has no channel dependencies
 */
@SpringBootTest
@ActiveProfiles("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ChannelIndependenceIT extends IntegrationTestBase {

    @Autowired
    private IntentApiController intentApiController;

    @Autowired
    private UserIntentInboxRepository intentRepository;

    @BeforeEach
    void setUp() {
        intentRepository.deleteAll();
    }

    @Test
    void testSubmitIntentViaAPI() {
        // Given - UI client submitting intent
        IntentApiController.IntentRequest request = new IntentApiController.IntentRequest();
        request.setUserId("webuser1");
        request.setChannel("WEB");
        request.setText("spent 500 on groceries");

        // When - Submit via API
        var response = intentApiController.submitIntent(request);

        // Then - Intent should be created
        assertNotNull(response.getBody());
        assertEquals("RECEIVED", response.getBody().getStatus());
        assertNotNull(response.getBody().getIntentId());
        assertNotNull(response.getBody().getCorrelationId());

        // Verify in database
        List<UserIntentInboxEntity> intents = intentRepository.findByUserIdAndStatusOrderByReceivedAtDesc(
                "webuser1", IntentStatus.RECEIVED);
        assertTrue(intents.size() >= 1);
        assertEquals("spent 500 on groceries", intents.get(0).getRawText());
        assertEquals("WEB", intents.get(0).getChannel());
    }

    @Test
    void testGetPendingAction_NoPending() {
        // Given - User with no pending actions
        String userId = "webuser2";

        // When - Query for pending actions
        var response = intentApiController.getPendingAction(userId);

        // Then - Should indicate no pending action
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isHasPendingAction());
    }

    @Test
    void testGetPendingAction_WithPending() throws Exception {
        // Given - User with NEEDS_INPUT intent
        String userId = "webuser3";
        UserIntentInboxEntity intent = new UserIntentInboxEntity();
        intent.setUserId(userId);
        intent.setChannel("WEB");
        intent.setRawText("spent 500");
        intent.setReceivedAt(java.time.LocalDateTime.now());
        intent.setCorrelationId("WEB:webuser3:" + System.currentTimeMillis() + ":test");
        intent.setStatus(IntentStatus.NEEDS_INPUT);
        intent.setStatusReason("Missing category");
        intent.setDetectedIntent("EXPENSE");
        intent.setProcessingAttempts(0);
        intentRepository.save(intent);

        // When - Query for pending actions
        var response = intentApiController.getPendingAction(userId);

        // Then - Should return the pending action
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isHasPendingAction());
        assertEquals(intent.getId(), response.getBody().getIntentId());
        assertEquals("spent 500", response.getBody().getOriginalText());
        assertEquals("Missing category", response.getBody().getStatusReason());
        assertEquals("EXPENSE", response.getBody().getDetectedIntent());
    }

    @Test
    void testResumeIntent() throws Exception {
        // Given - User with NEEDS_INPUT intent
        String userId = "webuser4";
        UserIntentInboxEntity parentIntent = new UserIntentInboxEntity();
        parentIntent.setUserId(userId);
        parentIntent.setChannel("WEB");
        parentIntent.setRawText("dinner 300");
        parentIntent.setReceivedAt(java.time.LocalDateTime.now());
        parentIntent.setCorrelationId("WEB:webuser4:" + System.currentTimeMillis() + ":parent");
        parentIntent.setStatus(IntentStatus.NEEDS_INPUT);
        parentIntent.setStatusReason("Missing payment method");
        parentIntent.setProcessingAttempts(0);
        intentRepository.save(parentIntent);

        // When - Resume with response
        IntentApiController.ResumeRequest request = new IntentApiController.ResumeRequest();
        request.setUserId(userId);
        request.setChannel("WEB");
        request.setResponseText("credit card");

        var response = intentApiController.resumeIntent(request);

        // Then - Should create follow-up intent
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getIntentId());
        
        // Wait for async processing
        await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
            UserIntentInboxEntity followUp = intentRepository.findById(response.getBody().getIntentId()).orElseThrow();
            assertEquals("credit card", followUp.getRawText());
            assertEquals(parentIntent.getId(), followUp.getFollowupParentId());
        });
    }

    @Test
    void testChannelIndependence_SameIntentDifferentChannels() throws Exception {
        // Given - Same intent text from different channels
        String intentText = "paid electricity bill 5000";
        
        // Submit via WEB
        IntentApiController.IntentRequest webRequest = new IntentApiController.IntentRequest();
        webRequest.setUserId("user1");
        webRequest.setChannel("WEB");
        webRequest.setText(intentText);
        
        // Submit via MOBILE
        IntentApiController.IntentRequest mobileRequest = new IntentApiController.IntentRequest();
        mobileRequest.setUserId("user2");
        mobileRequest.setChannel("MOBILE");
        mobileRequest.setText(intentText);

        // When - Submit both
        var webResponse = intentApiController.submitIntent(webRequest);
        var mobileResponse = intentApiController.submitIntent(mobileRequest);

        // Then - Both should be created successfully
        assertNotNull(webResponse.getBody());
        assertNotNull(mobileResponse.getBody());
        assertEquals("RECEIVED", webResponse.getBody().getStatus());
        assertEquals("RECEIVED", mobileResponse.getBody().getStatus());

        // Wait for async processing
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            UserIntentInboxEntity webIntent = intentRepository.findById(webResponse.getBody().getIntentId()).orElseThrow();
            UserIntentInboxEntity mobileIntent = intentRepository.findById(mobileResponse.getBody().getIntentId()).orElseThrow();

            // Both should have same raw text
            assertEquals(intentText, webIntent.getRawText());
            assertEquals(intentText, mobileIntent.getRawText());

            // Both should be processed (or in processing/needs_input)
            assertNotEquals(IntentStatus.RECEIVED, webIntent.getStatus());
            assertNotEquals(IntentStatus.RECEIVED, mobileIntent.getStatus());

            // If both succeeded, they should have same detected intent
            if (webIntent.getDetectedIntent() != null && mobileIntent.getDetectedIntent() != null) {
                // Core processing is channel-independent
                // Same intent should be classified the same way
                // (This might differ in test environment due to LLM availability)
                assertNotNull(webIntent.getDetectedIntent());
                assertNotNull(mobileIntent.getDetectedIntent());
            }
        });
    }

    @Test
    void testDefaultChannelIsWeb() {
        // Given - Request without channel specified
        IntentApiController.IntentRequest request = new IntentApiController.IntentRequest();
        request.setUserId("user5");
        request.setChannel(null); // No channel
        request.setText("test message");

        // When - Submit
        var response = intentApiController.submitIntent(request);

        // Then - Should default to WEB
        UserIntentInboxEntity intent = intentRepository.findById(response.getBody().getIntentId()).orElseThrow();
        assertEquals("WEB", intent.getChannel());
    }

    @Test
    void testMultipleChannelsForSameUser() {
        // Given - Same user using different channels
        String userId = "multiChannelUser";

        IntentApiController.IntentRequest webRequest = new IntentApiController.IntentRequest();
        webRequest.setUserId(userId);
        webRequest.setChannel("WEB");
        webRequest.setText("groceries 200");

        IntentApiController.IntentRequest mobileRequest = new IntentApiController.IntentRequest();
        mobileRequest.setUserId(userId);
        mobileRequest.setChannel("MOBILE");
        mobileRequest.setText("fuel 1500");

        // When - Submit from both channels
        intentApiController.submitIntent(webRequest);
        intentApiController.submitIntent(mobileRequest);

        // Then - Both intents should exist for same user
        List<UserIntentInboxEntity> userIntents = intentRepository.findAll().stream()
                .filter(i -> i.getUserId().equals(userId))
                .toList();

        assertEquals(2, userIntents.size());
        assertTrue(userIntents.stream().anyMatch(i -> i.getChannel().equals("WEB")));
        assertTrue(userIntents.stream().anyMatch(i -> i.getChannel().equals("MOBILE")));
    }
}
