package com.apps.deen_sa.core.intent;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Phase 1: Intent Ingestion & Staging Service
 * 
 * Handles persistence of user intents to the staging inbox.
 * 
 * Key responsibilities:
 * - Persist intent immediately on receipt
 * - Generate correlation IDs for deduplication
 * - Provide immediate acknowledgment
 * - NO inline handler execution
 * - NO financial data processing
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class IntentInboxService {

    private final UserIntentInboxRepository repository;

    /**
     * Persist a user intent to the inbox.
     * 
     * This method:
     * - Creates a unique correlation ID
     * - Persists the intent immediately
     * - Returns the persisted entity
     * - Does NOT execute any handlers
     * - Does NOT process financial data
     * 
     * @param userId User identifier
     * @param channel Channel of origin (e.g., WHATSAPP, WEB)
     * @param rawText Raw user input
     * @return Persisted intent entity
     */
    @Transactional
    public UserIntentInboxEntity persistIntent(String userId, String channel, String rawText) {
        String correlationId = generateCorrelationId(channel, userId);
        
        // Check for duplicate (idempotency)
        if (repository.existsByCorrelationId(correlationId)) {
            log.warn("Duplicate intent detected with correlation_id: {}", correlationId);
            return repository.findByCorrelationId(correlationId)
                    .orElseThrow(() -> new IllegalStateException("Correlation ID exists but not found"));
        }

        UserIntentInboxEntity entity = UserIntentInboxEntity.builder()
                .userId(userId)
                .channel(channel)
                .correlationId(correlationId)
                .rawText(rawText)
                .receivedAt(LocalDateTime.now())
                .status(IntentStatus.RECEIVED)
                .processingAttempts(0)
                .build();

        UserIntentInboxEntity saved = repository.save(entity);
        log.info("Intent persisted: id={}, correlation_id={}, user_id={}, channel={}", 
                saved.getId(), saved.getCorrelationId(), saved.getUserId(), saved.getChannel());
        
        return saved;
    }

    /**
     * Update intent with detected intent and confidence.
     * 
     * Called after LLM classification to enrich the intent record.
     * 
     * @param intentId Intent ID
     * @param detectedIntent Detected intent type
     * @param confidence Confidence score (0.0 to 1.0)
     */
    @Transactional
    public void updateDetectedIntent(Long intentId, String detectedIntent, BigDecimal confidence) {
        UserIntentInboxEntity entity = repository.findById(intentId)
                .orElseThrow(() -> new IllegalArgumentException("Intent not found: " + intentId));
        
        entity.setDetectedIntent(detectedIntent);
        entity.setIntentConfidence(confidence);
        repository.save(entity);
        
        log.debug("Intent classification updated: id={}, intent={}, confidence={}", 
                intentId, detectedIntent, confidence);
    }

    /**
     * Update intent status.
     * 
     * @param intentId Intent ID
     * @param status New status
     * @param reason Optional reason for status change
     */
    @Transactional
    public void updateStatus(Long intentId, IntentStatus status, String reason) {
        UserIntentInboxEntity entity = repository.findById(intentId)
                .orElseThrow(() -> new IllegalArgumentException("Intent not found: " + intentId));
        
        entity.setStatus(status);
        if (reason != null) {
            entity.setStatusReason(reason);
        }
        repository.save(entity);
        
        log.info("Intent status updated: id={}, status={}, reason={}", 
                intentId, status, reason);
    }

    /**
     * Generate a unique correlation ID for an intent.
     * 
     * Format: {channel}:{userId}:{timestamp}:{uuid}
     * 
     * @param channel Channel name
     * @param userId User ID
     * @return Correlation ID
     */
    private String generateCorrelationId(String channel, String userId) {
        long timestamp = System.currentTimeMillis();
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return String.format("%s:%s:%d:%s", channel, userId, timestamp, uuid);
    }
}
