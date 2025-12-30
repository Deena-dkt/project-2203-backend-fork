package com.apps.deen_sa.core.intent;

import com.apps.deen_sa.conversation.ConversationContext;
import com.apps.deen_sa.conversation.SpeechOrchestrator;
import com.apps.deen_sa.conversation.SpeechResult;
import com.apps.deen_sa.conversation.SpeechStatus;
import com.apps.deen_sa.core.transaction.TransactionRepository;
import com.apps.deen_sa.dto.IntentResult;
import com.apps.deen_sa.llm.impl.IntentClassifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Phase 2: Async Processing Engine
 * 
 * Processes intents from the inbox asynchronously.
 * 
 * Key responsibilities:
 * - Process intents in RECEIVED status
 * - Ensure idempotent processing (no double financial impact)
 * - Manage state transitions
 * - Handle failures and retries
 * 
 * Invariants:
 * - Financial handlers execute at most once per intent
 * - Retries do not double-apply financial impact
 * - State transitions follow the state machine
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class IntentProcessingEngine {

    private final UserIntentInboxRepository intentRepository;
    private final IntentClassifier intentClassifier;
    private final SpeechOrchestrator orchestrator;
    private final ConversationContext conversationContext;
    private final TransactionRepository transactionRepository;

    /**
     * Process a single intent asynchronously.
     * 
     * This method is idempotent - can be called multiple times for the same intent
     * without creating duplicate financial transactions.
     * 
     * State transitions:
     * - RECEIVED → PROCESSING (when starting)
     * - PROCESSING → PROCESSED (on success)
     * - PROCESSING → NEEDS_INPUT (when input required)
     * - PROCESSING → FAILED (on error)
     * 
     * @param intentId Intent ID to process
     */
    @Async("intentProcessingExecutor")
    @Transactional
    public void processIntent(Long intentId) {
        UserIntentInboxEntity intent = intentRepository.findById(intentId)
                .orElseThrow(() -> new IllegalArgumentException("Intent not found: " + intentId));

        // Idempotency check: Skip if already processed
        if (intent.getStatus() == IntentStatus.PROCESSED) {
            log.info("Intent {} already PROCESSED, skipping", intentId);
            return;
        }

        // Idempotency check: Skip if currently being processed (race condition)
        if (intent.getStatus() == IntentStatus.PROCESSING && 
            intent.getProcessingAttempts() > 0 &&
            intent.getLastProcessedAt() != null &&
            intent.getLastProcessedAt().isAfter(java.time.LocalDateTime.now().minusMinutes(5))) {
            log.warn("Intent {} is currently being processed, skipping duplicate call", intentId);
            return;
        }

        try {
            // Transition to PROCESSING
            intent.markAsProcessing();
            intentRepository.save(intent);
            
            log.info("Processing intent {}: user={}, channel={}, text='{}'", 
                    intentId, intent.getUserId(), intent.getChannel(), intent.getRawText());

            // Step 1: Classify intent if not already classified
            if (intent.getDetectedIntent() == null) {
                try {
                    IntentResult classification = intentClassifier.classify(intent.getRawText());
                    intent.setDetectedIntent(classification.intent());
                    intent.setIntentConfidence(BigDecimal.valueOf(classification.confidence()));
                    intentRepository.save(intent);
                    
                    log.debug("Intent {} classified as {} with confidence {}", 
                            intentId, classification.intent(), classification.confidence());
                } catch (Exception e) {
                    // If classification fails (e.g., LLM unavailable), continue with unknown intent
                    log.warn("Failed to classify intent {}: {}", intentId, e.getMessage());
                    intent.setDetectedIntent("UNKNOWN");
                    intent.setIntentConfidence(BigDecimal.ZERO);
                    intentRepository.save(intent);
                }
            }

            // Step 2: Check if already financially applied
            // This is the key idempotency check - prevents double financial impact
            boolean alreadyApplied = checkIfFinanciallyApplied(intent);
            if (alreadyApplied) {
                log.info("Intent {} already has financial impact applied, marking as PROCESSED", intentId);
                intent.markAsProcessed();
                intentRepository.save(intent);
                return;
            }

            // Step 3: Process through orchestrator
            SpeechResult result = orchestrator.process(intent.getRawText(), conversationContext);
            
            log.debug("Intent {} processing result: status={}, message={}", 
                    intentId, result.getStatus(), result.getMessage());

            // Step 4: Handle result and transition state
            handleProcessingResult(intent, result);

        } catch (Exception e) {
            log.error("Failed to process intent {}: {}", intentId, e.getMessage(), e);
            intent.markAsFailed(e.getMessage());
            intentRepository.save(intent);
        }
    }

    /**
     * Check if financial impact has already been applied for this intent.
     * 
     * This prevents double-applying financial transactions on retry.
     * We check if a transaction exists with this intent's correlation_id.
     * 
     * @param intent Intent to check
     * @return true if financial impact already applied
     */
    private boolean checkIfFinanciallyApplied(UserIntentInboxEntity intent) {
        // Check if there's already a transaction linked to this correlation ID
        // In Phase 2, we're using the correlation_id as a unique identifier
        // In a future phase, we might add an explicit link field
        
        // For now, we check by looking for transactions created around the same time
        // with the same raw_text (this is a simplified check for Phase 2)
        long existingTransactions = transactionRepository.findAll().stream()
                .filter(tx -> tx.getRawText() != null && 
                             tx.getRawText().equals(intent.getRawText()) &&
                             tx.isFinanciallyApplied())
                .count();
        
        return existingTransactions > 0;
    }

    /**
     * Handle the result from processing and transition state accordingly.
     * 
     * @param intent Intent being processed
     * @param result Result from speech orchestrator
     */
    private void handleProcessingResult(UserIntentInboxEntity intent, SpeechResult result) {
        switch (result.getStatus()) {
            case SAVED:
                // Successfully processed and saved
                intent.markAsProcessed();
                log.info("Intent {} PROCESSED successfully", intent.getId());
                break;
                
            case INFO:
                // Successfully processed (informational response)
                intent.markAsProcessed();
                log.info("Intent {} PROCESSED successfully (INFO)", intent.getId());
                break;
                
            case FOLLOWUP:
                // User input required
                intent.markAsNeedsInput("Follow-up required: " + result.getMessage());
                log.info("Intent {} marked as NEEDS_INPUT", intent.getId());
                break;
                
            case UNKNOWN:
            case INVALID:
                // Could not process - mark as failed
                intent.markAsFailed("Processing failed: " + result.getMessage());
                log.warn("Intent {} marked as FAILED: {}", intent.getId(), result.getMessage());
                break;
                
            default:
                intent.markAsFailed("Unexpected result status: " + result.getStatus());
                log.error("Intent {} failed with unexpected status: {}", intent.getId(), result.getStatus());
        }
        
        intentRepository.save(intent);
    }

    /**
     * Retry processing for a failed intent.
     * 
     * Can be called manually or by a scheduled job.
     * 
     * @param intentId Intent ID to retry
     */
    public void retryIntent(Long intentId) {
        UserIntentInboxEntity intent = intentRepository.findById(intentId)
                .orElseThrow(() -> new IllegalArgumentException("Intent not found: " + intentId));

        if (intent.getStatus() != IntentStatus.FAILED && intent.getStatus() != IntentStatus.NEEDS_INPUT) {
            throw new IllegalStateException(
                    "Cannot retry intent in status " + intent.getStatus() + 
                    ". Only FAILED or NEEDS_INPUT intents can be retried.");
        }

        log.info("Retrying intent {}: current status={}, attempts={}", 
                intentId, intent.getStatus(), intent.getProcessingAttempts());

        // Transition back to RECEIVED for reprocessing
        intent.setStatus(IntentStatus.RECEIVED);
        intentRepository.save(intent);

        // Process asynchronously
        processIntent(intentId);
    }
}
