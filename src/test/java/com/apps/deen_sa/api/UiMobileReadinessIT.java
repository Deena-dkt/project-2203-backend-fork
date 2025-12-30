package com.apps.deen_sa.api;

import com.apps.deen_sa.IntegrationTestBase;
import com.apps.deen_sa.core.intent.IntentInboxService;
import com.apps.deen_sa.core.intent.IntentStatus;
import com.apps.deen_sa.core.intent.UserIntentInboxEntity;
import com.apps.deen_sa.core.intent.UserIntentInboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 6: UI/Mobile Readiness Integration Tests
 * 
 * Tests for REST API pagination, ordering, and error handling.
 */
@SpringBootTest
@ActiveProfiles("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class UiMobileReadinessIT extends IntegrationTestBase {

    @Autowired
    private IntentApiController apiController;

    @Autowired
    private IntentInboxService intentInboxService;

    @Autowired
    private UserIntentInboxRepository intentRepository;

    @BeforeEach
    void setUp() {
        intentRepository.deleteAll();
    }

    // ============================================================================
    // PAGINATION TESTS
    // ============================================================================

    @Test
    void testPagination_FirstPage() {
        // Given - User with 25 intents
        String userId = "pagination_user1";
        for (int i = 0; i < 25; i++) {
            intentInboxService.persistIntent(userId, "WEB", "message " + i);
        }

        // When - Request first page (size=10)
        ResponseEntity<IntentApiController.IntentHistoryResponse> response = 
                apiController.getIntentHistory(userId, 0, 10, "receivedAt", "DESC", null);

        // Then - Should return first 10 items
        assertNotNull(response.getBody());
        assertEquals(10, response.getBody().getItems().size());
        assertEquals(25, response.getBody().getTotalItems());
        assertEquals(3, response.getBody().getTotalPages()); // 25 items / 10 per page = 3 pages
        assertEquals(0, response.getBody().getCurrentPage());
        assertTrue(response.getBody().isHasNext());
        assertFalse(response.getBody().isHasPrevious());
    }

    @Test
    void testPagination_MiddlePage() {
        // Given - User with 25 intents
        String userId = "pagination_user2";
        for (int i = 0; i < 25; i++) {
            intentInboxService.persistIntent(userId, "WEB", "message " + i);
        }

        // When - Request second page (size=10)
        ResponseEntity<IntentApiController.IntentHistoryResponse> response = 
                apiController.getIntentHistory(userId, 1, 10, "receivedAt", "DESC", null);

        // Then - Should return middle page
        assertNotNull(response.getBody());
        assertEquals(10, response.getBody().getItems().size());
        assertEquals(1, response.getBody().getCurrentPage());
        assertTrue(response.getBody().isHasNext());
        assertTrue(response.getBody().isHasPrevious());
    }

    @Test
    void testPagination_LastPage() {
        // Given - User with 25 intents
        String userId = "pagination_user3";
        for (int i = 0; i < 25; i++) {
            intentInboxService.persistIntent(userId, "WEB", "message " + i);
        }

        // When - Request last page (size=10)
        ResponseEntity<IntentApiController.IntentHistoryResponse> response = 
                apiController.getIntentHistory(userId, 2, 10, "receivedAt", "DESC", null);

        // Then - Should return last 5 items
        assertNotNull(response.getBody());
        assertEquals(5, response.getBody().getItems().size()); // Only 5 items on last page
        assertEquals(2, response.getBody().getCurrentPage());
        assertFalse(response.getBody().isHasNext());
        assertTrue(response.getBody().isHasPrevious());
    }

    @Test
    void testPagination_EmptyPage() {
        // Given - User with no intents
        String userId = "pagination_user_empty";

        // When - Request first page
        ResponseEntity<IntentApiController.IntentHistoryResponse> response = 
                apiController.getIntentHistory(userId, 0, 10, "receivedAt", "DESC", null);

        // Then - Should return empty result
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().getItems().size());
        assertEquals(0, response.getBody().getTotalItems());
        assertEquals(0, response.getBody().getTotalPages());
        assertFalse(response.getBody().isHasNext());
        assertFalse(response.getBody().isHasPrevious());
    }

    @Test
    void testPagination_MaxSizeLimit() {
        // Given - User with 150 intents
        String userId = "pagination_user_large";
        for (int i = 0; i < 150; i++) {
            intentInboxService.persistIntent(userId, "WEB", "message " + i);
        }

        // When - Request with size > 100 (should be limited to 100)
        ResponseEntity<IntentApiController.IntentHistoryResponse> response = 
                apiController.getIntentHistory(userId, 0, 200, "receivedAt", "DESC", null);

        // Then - Should limit to max 100 items
        assertNotNull(response.getBody());
        assertEquals(100, response.getBody().getItems().size());
        assertEquals(100, response.getBody().getPageSize());
    }

    // ============================================================================
    // ORDERING TESTS
    // ============================================================================

    @Test
    void testOrdering_ByReceivedAtDescending() throws Exception {
        // Given - User with intents at different times
        String userId = "ordering_user1";
        
        UserIntentInboxEntity intent1 = intentInboxService.persistIntent(userId, "WEB", "first");
        Thread.sleep(100);
        UserIntentInboxEntity intent2 = intentInboxService.persistIntent(userId, "WEB", "second");
        Thread.sleep(100);
        UserIntentInboxEntity intent3 = intentInboxService.persistIntent(userId, "WEB", "third");

        // When - Request with DESC ordering
        ResponseEntity<IntentApiController.IntentHistoryResponse> response = 
                apiController.getIntentHistory(userId, 0, 10, "receivedAt", "DESC", null);

        // Then - Should be ordered newest first
        assertNotNull(response.getBody());
        assertEquals(3, response.getBody().getItems().size());
        assertEquals(intent3.getId(), response.getBody().getItems().get(0).getIntentId());
        assertEquals(intent2.getId(), response.getBody().getItems().get(1).getIntentId());
        assertEquals(intent1.getId(), response.getBody().getItems().get(2).getIntentId());
    }

    @Test
    void testOrdering_ByReceivedAtAscending() throws Exception {
        // Given - User with intents at different times
        String userId = "ordering_user2";
        
        UserIntentInboxEntity intent1 = intentInboxService.persistIntent(userId, "WEB", "first");
        Thread.sleep(100);
        UserIntentInboxEntity intent2 = intentInboxService.persistIntent(userId, "WEB", "second");
        Thread.sleep(100);
        UserIntentInboxEntity intent3 = intentInboxService.persistIntent(userId, "WEB", "third");

        // When - Request with ASC ordering
        ResponseEntity<IntentApiController.IntentHistoryResponse> response = 
                apiController.getIntentHistory(userId, 0, 10, "receivedAt", "ASC", null);

        // Then - Should be ordered oldest first
        assertNotNull(response.getBody());
        assertEquals(3, response.getBody().getItems().size());
        assertEquals(intent1.getId(), response.getBody().getItems().get(0).getIntentId());
        assertEquals(intent2.getId(), response.getBody().getItems().get(1).getIntentId());
        assertEquals(intent3.getId(), response.getBody().getItems().get(2).getIntentId());
    }

    // ============================================================================
    // STATUS FILTER TESTS
    // ============================================================================

    @Test
    void testStatusFilter_OnlyProcessed() {
        // Given - User with mixed status intents
        String userId = "filter_user1";
        
        UserIntentInboxEntity intent1 = intentInboxService.persistIntent(userId, "WEB", "message 1");
        intent1.setStatus(IntentStatus.PROCESSED);
        intentRepository.save(intent1);
        
        UserIntentInboxEntity intent2 = intentInboxService.persistIntent(userId, "WEB", "message 2");
        intent2.setStatus(IntentStatus.FAILED);
        intentRepository.save(intent2);
        
        UserIntentInboxEntity intent3 = intentInboxService.persistIntent(userId, "WEB", "message 3");
        intent3.setStatus(IntentStatus.PROCESSED);
        intentRepository.save(intent3);

        // When - Filter by PROCESSED status
        ResponseEntity<IntentApiController.IntentHistoryResponse> response = 
                apiController.getIntentHistory(userId, 0, 10, "receivedAt", "DESC", "PROCESSED");

        // Then - Should return only PROCESSED intents
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().getItems().size());
        assertTrue(response.getBody().getItems().stream()
                .allMatch(item -> item.getStatus().equals("PROCESSED")));
    }

    @Test
    void testStatusFilter_InvalidStatus() {
        // Given - User with intents
        String userId = "filter_user2";
        intentInboxService.persistIntent(userId, "WEB", "message 1");
        intentInboxService.persistIntent(userId, "WEB", "message 2");

        // When - Filter by invalid status
        ResponseEntity<IntentApiController.IntentHistoryResponse> response = 
                apiController.getIntentHistory(userId, 0, 10, "receivedAt", "DESC", "INVALID_STATUS");

        // Then - Should return all intents (ignores invalid filter)
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().getItems().size());
    }

    // ============================================================================
    // VALIDATION TESTS
    // ============================================================================

    @Test
    void testValidation_SubmitIntent_MissingUserId() {
        // Given - Request without userId
        IntentApiController.IntentRequest request = new IntentApiController.IntentRequest();
        request.setText("test message");

        // When - Submit intent
        ResponseEntity<IntentApiController.IntentResponse> response = apiController.submitIntent(request);

        // Then - Should return 400 Bad Request
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getMessage().contains("userId"));
    }

    @Test
    void testValidation_SubmitIntent_MissingText() {
        // Given - Request without text
        IntentApiController.IntentRequest request = new IntentApiController.IntentRequest();
        request.setUserId("test_user");

        // When - Submit intent
        ResponseEntity<IntentApiController.IntentResponse> response = apiController.submitIntent(request);

        // Then - Should return 400 Bad Request
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getMessage().contains("text"));
    }

    // ============================================================================
    // ERROR HANDLING TESTS
    // ============================================================================

    @Test
    void testErrorHandling_ValidSubmission() {
        // Given - Valid request
        IntentApiController.IntentRequest request = new IntentApiController.IntentRequest();
        request.setUserId("error_test_user");
        request.setText("test message");
        request.setChannel("WEB");

        // When - Submit intent
        ResponseEntity<IntentApiController.IntentResponse> response = apiController.submitIntent(request);

        // Then - Should succeed
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getIntentId());
        assertEquals("Intent submitted successfully", response.getBody().getMessage());
    }

    @Test
    void testErrorHandling_ResumeWithoutPending() {
        // Given - User without pending NEEDS_INPUT
        String userId = "resume_test_user";
        
        IntentApiController.ResumeRequest request = new IntentApiController.ResumeRequest();
        request.setUserId(userId);
        request.setResponseText("my response");
        request.setChannel("WEB");

        // When - Attempt to resume
        ResponseEntity<IntentApiController.IntentResponse> response = apiController.resumeIntent(request);

        // Then - Should handle gracefully (creates new intent since no pending)
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getIntentId());
    }

    // ============================================================================
    // COMPLETE WORKFLOW TESTS
    // ============================================================================

    @Test
    void testCompleteWorkflow_SubmitCheckHistoryResume() throws Exception {
        // Given - New user
        String userId = "workflow_user";

        // Step 1: Submit intent
        IntentApiController.IntentRequest submitRequest = new IntentApiController.IntentRequest();
        submitRequest.setUserId(userId);
        submitRequest.setText("spent 500 on food");
        submitRequest.setChannel("WEB");
        
        ResponseEntity<IntentApiController.IntentResponse> submitResponse = 
                apiController.submitIntent(submitRequest);
        assertNotNull(submitResponse.getBody());
        Long intentId = submitResponse.getBody().getIntentId();

        Thread.sleep(100);

        // Step 2: Check history
        ResponseEntity<IntentApiController.IntentHistoryResponse> historyResponse = 
                apiController.getIntentHistory(userId, 0, 10, "receivedAt", "DESC", null);
        assertNotNull(historyResponse.getBody());
        assertEquals(1, historyResponse.getBody().getItems().size());
        assertEquals(intentId, historyResponse.getBody().getItems().get(0).getIntentId());

        // Step 3: Simulate NEEDS_INPUT state
        UserIntentInboxEntity intent = intentRepository.findById(intentId).orElseThrow();
        intent.setStatus(IntentStatus.NEEDS_INPUT);
        intent.setStatusReason("Missing category");
        intentRepository.save(intent);

        // Step 4: Check pending action
        ResponseEntity<IntentApiController.PendingActionResponse> pendingResponse = 
                apiController.getPendingAction(userId);
        assertNotNull(pendingResponse.getBody());
        assertTrue(pendingResponse.getBody().isHasPendingAction());
        assertEquals("Missing category", pendingResponse.getBody().getStatusReason());

        // Step 5: Resume with response
        IntentApiController.ResumeRequest resumeRequest = new IntentApiController.ResumeRequest();
        resumeRequest.setUserId(userId);
        resumeRequest.setResponseText("groceries");
        resumeRequest.setChannel("WEB");
        
        ResponseEntity<IntentApiController.IntentResponse> resumeResponse = 
                apiController.resumeIntent(resumeRequest);
        assertNotNull(resumeResponse.getBody());
        assertNotNull(resumeResponse.getBody().getIntentId());

        Thread.sleep(100);

        // Step 6: Check history again (should have 2 intents now)
        ResponseEntity<IntentApiController.IntentHistoryResponse> finalHistoryResponse = 
                apiController.getIntentHistory(userId, 0, 10, "receivedAt", "DESC", null);
        assertNotNull(finalHistoryResponse.getBody());
        assertEquals(2, finalHistoryResponse.getBody().getItems().size());
    }
}
