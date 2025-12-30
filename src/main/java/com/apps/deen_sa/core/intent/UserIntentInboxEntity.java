package com.apps.deen_sa.core.intent;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Phase 1: Intent Ingestion & Staging
 * 
 * Represents a staged user intent before processing.
 * This is a write-ahead log for all incoming user intents.
 * 
 * Invariants:
 * - raw_text must never be mutated after creation
 * - No financial data is stored here
 * - No handlers are executed inline during persistence
 * - Only status-related fields are mutable after creation
 */
@Entity
@Table(name = "user_intent_inbox")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserIntentInboxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * User identifier (e.g., phone number, user ID)
     */
    @Column(name = "user_id", nullable = false)
    private String userId;

    /**
     * Channel through which intent was received (e.g., WHATSAPP, WEB, API)
     */
    @Column(name = "channel", nullable = false, length = 50)
    private String channel;

    /**
     * Unique correlation ID for deduplication and tracking
     * Format: {channel}:{userId}:{timestamp}:{random}
     */
    @Column(name = "correlation_id", nullable = false, unique = true)
    private String correlationId;

    /**
     * Raw unprocessed text from user.
     * IMMUTABLE - never mutate after creation.
     */
    @Column(name = "raw_text", nullable = false, columnDefinition = "TEXT")
    private String rawText;

    /**
     * When the intent was received
     */
    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    /**
     * Detected intent type (e.g., EXPENSE, INCOME, QUERY)
     * Populated during initial classification
     */
    @Column(name = "detected_intent", length = 100)
    private String detectedIntent;

    /**
     * Confidence score of intent detection (0.0 to 1.0)
     */
    @Column(name = "intent_confidence", precision = 5, scale = 4)
    private BigDecimal intentConfidence;

    /**
     * Current processing status
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private IntentStatus status = IntentStatus.RECEIVED;

    /**
     * Reason for current status (e.g., error message if FAILED)
     */
    @Column(name = "status_reason", columnDefinition = "TEXT")
    private String statusReason;

    /**
     * Missing fields that prevent processing (JSON object)
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "missing_fields", columnDefinition = "jsonb")
    private Map<String, Object> missingFields;

    /**
     * Number of times processing has been attempted
     */
    @Column(name = "processing_attempts", nullable = false)
    @Builder.Default
    private Integer processingAttempts = 0;

    /**
     * When the intent was last processed (or attempted to be processed)
     */
    @Column(name = "last_processed_at")
    private LocalDateTime lastProcessedAt;

    /**
     * Increment processing attempts and update last processed timestamp
     */
    public void recordProcessingAttempt() {
        this.processingAttempts++;
        this.lastProcessedAt = LocalDateTime.now();
    }

    /**
     * Mark as processing
     */
    public void markAsProcessing() {
        this.status = IntentStatus.PROCESSING;
        recordProcessingAttempt();
    }

    /**
     * Mark as completed successfully
     */
    public void markAsProcessed() {
        this.status = IntentStatus.PROCESSED;
    }
    
    /**
     * Mark as needing input
     */
    public void markAsNeedsInput(String reason) {
        this.status = IntentStatus.NEEDS_INPUT;
        this.statusReason = reason;
    }

    /**
     * Mark as failed with reason
     */
    public void markAsFailed(String reason) {
        this.status = IntentStatus.FAILED;
        this.statusReason = reason;
    }

    /**
     * Mark as ignored with reason
     */
    public void markAsIgnored(String reason) {
        this.status = IntentStatus.IGNORED;
        this.statusReason = reason;
    }
}
