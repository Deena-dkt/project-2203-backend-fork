package com.apps.deen_sa.api;

import com.apps.deen_sa.core.intent.ChannelAgnosticIntentService;
import com.apps.deen_sa.core.intent.UserIntentInboxEntity;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Phase 4: Channel-Independent Intent API
 * 
 * Exposes backend APIs for UI/Mobile clients using state-based interaction.
 * 
 * UI/Mobile flow:
 * 1. Submit intent via POST /api/v1/intents
 * 2. Check for pending actions via GET /api/v1/intents/pending
 * 3. Resume with response via POST /api/v1/intents/resume
 * 
 * This is distinct from WhatsApp's message-based interaction.
 */
@RestController
@RequestMapping("/api/v1/intents")
@RequiredArgsConstructor
@Log4j2
public class IntentApiController {

    private final ChannelAgnosticIntentService intentService;

    /**
     * Submit a new intent from UI/Mobile.
     * 
     * @param request Intent submission request
     * @return Response with intent ID and status
     */
    @PostMapping
    public ResponseEntity<IntentResponse> submitIntent(@RequestBody IntentRequest request) {
        log.info("Received intent submission from user {} via {}: '{}'", 
                request.getUserId(), request.getChannel(), request.getText());

        UserIntentInboxEntity intent = intentService.ingestIntent(
                request.getUserId(),
                request.getChannel() != null ? request.getChannel() : "WEB",
                request.getText()
        );

        IntentResponse response = new IntentResponse();
        response.setIntentId(intent.getId());
        response.setCorrelationId(intent.getCorrelationId());
        response.setStatus(intent.getStatus().toString());
        response.setMessage("Intent submitted successfully");

        return ResponseEntity.ok(response);
    }

    /**
     * Get pending action for user.
     * 
     * Returns the NEEDS_INPUT intent if user has unresolved follow-up.
     * UI/Mobile uses this to display follow-up prompts.
     * 
     * @param userId User identifier
     * @return Pending action, or null if none
     */
    @GetMapping("/pending")
    public ResponseEntity<PendingActionResponse> getPendingAction(@RequestParam String userId) {
        log.info("Checking for pending actions for user {}", userId);

        UserIntentInboxEntity pendingIntent = intentService.getPendingAction(userId);

        if (pendingIntent == null) {
            PendingActionResponse response = new PendingActionResponse();
            response.setHasPendingAction(false);
            return ResponseEntity.ok(response);
        }

        PendingActionResponse response = new PendingActionResponse();
        response.setHasPendingAction(true);
        response.setIntentId(pendingIntent.getId());
        response.setOriginalText(pendingIntent.getRawText());
        response.setStatusReason(pendingIntent.getStatusReason());
        response.setDetectedIntent(pendingIntent.getDetectedIntent());

        return ResponseEntity.ok(response);
    }

    /**
     * Resume processing with user's response.
     * 
     * UI/Mobile uses this to provide follow-up data.
     * 
     * @param request Resume request with user's response
     * @return Response with follow-up intent status
     */
    @PostMapping("/resume")
    public ResponseEntity<IntentResponse> resumeIntent(@RequestBody ResumeRequest request) {
        log.info("Received resume request from user {} via {}: '{}'",
                request.getUserId(), request.getChannel(), request.getResponseText());

        UserIntentInboxEntity followUpIntent = intentService.resumeWithResponse(
                request.getUserId(),
                request.getChannel() != null ? request.getChannel() : "WEB",
                request.getResponseText()
        );

        IntentResponse response = new IntentResponse();
        response.setIntentId(followUpIntent.getId());
        response.setCorrelationId(followUpIntent.getCorrelationId());
        response.setStatus(followUpIntent.getStatus().toString());
        response.setMessage("Resume request processed successfully");

        return ResponseEntity.ok(response);
    }

    /**
     * Request DTO for intent submission
     */
    @Data
    public static class IntentRequest {
        private String userId;
        private String channel;
        private String text;
    }

    /**
     * Response DTO for intent operations
     */
    @Data
    public static class IntentResponse {
        private Long intentId;
        private String correlationId;
        private String status;
        private String message;
    }

    /**
     * Request DTO for resume operation
     */
    @Data
    public static class ResumeRequest {
        private String userId;
        private String channel;
        private String responseText;
    }

    /**
     * Response DTO for pending action query
     */
    @Data
    public static class PendingActionResponse {
        private boolean hasPendingAction;
        private Long intentId;
        private String originalText;
        private String statusReason;
        private String detectedIntent;
    }
}
