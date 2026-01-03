package com.apps.deen_sa.api;

import com.apps.deen_sa.core.intent.ChannelAgnosticIntentService;
import com.apps.deen_sa.core.intent.IntentStatus;
import com.apps.deen_sa.core.intent.UserIntentInboxEntity;
import com.apps.deen_sa.core.intent.UserIntentInboxRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Phase 4 & 6: Channel-Independent Intent API
 * 
 * Exposes backend APIs for UI/Mobile clients using state-based interaction.
 * 
 * UI/Mobile flow:
 * 1. Submit intent via POST /api/v1/intents
 * 2. Check for pending actions via GET /api/v1/intents/pending
 * 3. Resume with response via POST /api/v1/intents/resume
 * 4. Query intent history via GET /api/v1/intents/history
 * 
 * This is distinct from WhatsApp's message-based interaction.
 * 
 * Phase 6 adds pagination, ordering, and comprehensive error handling.
 */
@RestController
@RequestMapping("/api/v1/intents")
@RequiredArgsConstructor
@Log4j2
public class IntentApiController {

    private final ChannelAgnosticIntentService intentService;
    private final UserIntentInboxRepository intentRepository;

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

        // Validate request
        if (request.getUserId() == null || request.getUserId().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(createErrorResponse("userId is required"));
        }
        if (request.getText() == null || request.getText().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(createErrorResponse("text is required"));
        }

        try {
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
        } catch (Exception e) {
            log.error("Error submitting intent for user {}: {}", request.getUserId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to submit intent: " + e.getMessage()));
        }
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

        // Validate parameter
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        try {
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
            response.setReceivedAt(pendingIntent.getReceivedAt());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching pending action for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
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

        // Validate request
        if (request.getUserId() == null || request.getUserId().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(createErrorResponse("userId is required"));
        }
        if (request.getResponseText() == null || request.getResponseText().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(createErrorResponse("responseText is required"));
        }

        try {
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
        } catch (Exception e) {
            log.error("Error resuming intent for user {}: {}", request.getUserId(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(createErrorResponse("Failed to resume intent: " + e.getMessage()));
        }
    }

    /**
     * Get intent history for user with pagination and ordering.
     * 
     * Phase 6: Allows UI/Mobile to display user's intent history.
     * 
     * @param userId User identifier
     * @param page Page number (0-indexed, default: 0)
     * @param size Page size (default: 20, max: 100)
     * @param sortBy Sort field (default: receivedAt)
     * @param sortDirection Sort direction (default: DESC)
     * @param status Optional status filter
     * @return Paginated intent history
     */
    @GetMapping("/history")
    public ResponseEntity<IntentHistoryResponse> getIntentHistory(
            @RequestParam String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "receivedAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection,
            @RequestParam(required = false) String status) {
        
        log.info("Fetching intent history for user {} (page={}, size={}, sort={}:{})", 
                userId, page, size, sortBy, sortDirection);

        // Validate parameters
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (page < 0) {
            page = 0;
        }
        if (size < 1) {
            size = 20;
        }

        try {
            // Limit page size to prevent abuse
            if (size > 100) {
                size = 100;
            }

            // Create sort
            Sort.Direction direction = sortDirection.equalsIgnoreCase("ASC") 
                    ? Sort.Direction.ASC 
                    : Sort.Direction.DESC;
            Sort sort = Sort.by(direction, sortBy);
            
            // Create pageable
            Pageable pageable = PageRequest.of(page, size, sort);

            // Fetch intents
            Page<UserIntentInboxEntity> intentsPage;
            if (status != null && !status.isBlank()) {
                try {
                    IntentStatus statusEnum = IntentStatus.valueOf(status.toUpperCase());
                    intentsPage = intentRepository.findByUserIdAndStatus(userId, statusEnum, pageable);
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid status filter: {}", status);
                    intentsPage = intentRepository.findByUserId(userId, pageable);
                }
            } else {
                intentsPage = intentRepository.findByUserId(userId, pageable);
            }

            // Convert to response
            List<IntentHistoryItem> items = intentsPage.getContent().stream()
                    .map(this::toHistoryItem)
                    .collect(Collectors.toList());

            IntentHistoryResponse response = new IntentHistoryResponse();
            response.setItems(items);
            response.setTotalItems(intentsPage.getTotalElements());
            response.setTotalPages(intentsPage.getTotalPages());
            response.setCurrentPage(page);
            response.setPageSize(size);
            response.setHasNext(intentsPage.hasNext());
            response.setHasPrevious(intentsPage.hasPrevious());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching intent history for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Helper method to create error response
     */
    private IntentResponse createErrorResponse(String message) {
        IntentResponse response = new IntentResponse();
        response.setMessage(message);
        return response;
    }

    /**
     * Helper method to convert entity to history item
     */
    private IntentHistoryItem toHistoryItem(UserIntentInboxEntity intent) {
        IntentHistoryItem item = new IntentHistoryItem();
        item.setIntentId(intent.getId());
        item.setCorrelationId(intent.getCorrelationId());
        item.setRawText(intent.getRawText());
        item.setStatus(intent.getStatus().toString());
        item.setDetectedIntent(intent.getDetectedIntent());
        // Convert BigDecimal to Double
        item.setIntentConfidence(intent.getIntentConfidence() != null ? intent.getIntentConfidence().doubleValue() : null);
        item.setStatusReason(intent.getStatusReason());
        item.setReceivedAt(intent.getReceivedAt());
        item.setLastProcessedAt(intent.getLastProcessedAt());
        item.setProcessingAttempts(intent.getProcessingAttempts());
        item.setChannel(intent.getChannel());
        return item;
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
        private LocalDateTime receivedAt;
    }

    /**
     * Response DTO for intent history (Phase 6)
     */
    @Data
    public static class IntentHistoryResponse {
        private List<IntentHistoryItem> items;
        private long totalItems;
        private int totalPages;
        private int currentPage;
        private int pageSize;
        private boolean hasNext;
        private boolean hasPrevious;
    }

    /**
     * Individual intent history item (Phase 6)
     */
    @Data
    public static class IntentHistoryItem {
        private Long intentId;
        private String correlationId;
        private String rawText;
        private String status;
        private String detectedIntent;
        private Double intentConfidence;
        private String statusReason;
        private LocalDateTime receivedAt;
        private LocalDateTime lastProcessedAt;
        private Integer processingAttempts;
        private String channel;
    }
}
