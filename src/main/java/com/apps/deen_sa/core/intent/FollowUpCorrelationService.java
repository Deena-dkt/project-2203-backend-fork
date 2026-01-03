package com.apps.deen_sa.core.intent;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Phase 3: Follow-Up Correlation Service
 * 
 * Handles correlation of follow-up messages with pending intents.
 * 
 * Key responsibilities:
 * - Detect if incoming message is a follow-up to pending intent
 * - Link follow-up intents to parent intents
 * - Ensure only one unresolved follow-up per user at a time
 * - Resume processing of parent intent with follow-up data
 * 
 * Invariants:
 * - Follow-ups are asynchronous (no blocking)
 * - Correlation is deterministic
 * - Only one NEEDS_INPUT intent per user at a time
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class FollowUpCorrelationService {

    private final UserIntentInboxRepository intentRepository;
    private final IntentProcessingEngine processingEngine;

    /**
     * Check if user has a pending follow-up request.
     * 
     * @param userId User identifier
     * @return Optional containing the pending intent awaiting input
     */
    public Optional<UserIntentInboxEntity> findPendingFollowUp(String userId) {
        return intentRepository.findFirstByUserIdAndStatusOrderByReceivedAtDesc(userId, IntentStatus.NEEDS_INPUT);
    }

    /**
     * Check if user can send new intent or must respond to follow-up.
     * 
     * This is informational - we still accept new intents, but may guide user
     * to complete pending follow-up first.
     * 
     * @param userId User identifier
     * @return true if user has unresolved follow-up
     */
    public boolean hasUnresolvedFollowUp(String userId) {
        return intentRepository.countByUserIdAndStatus(userId, IntentStatus.NEEDS_INPUT) > 0;
    }

    /**
     * Process incoming message as potential follow-up.
     * 
     * Decision flow:
     * 1. Check if user has pending NEEDS_INPUT intent
     * 2. If yes, treat this message as follow-up
     * 3. If no, treat as new intent (normal flow)
     * 
     * @param userId User identifier
     * @param channel Channel of origin
     * @param rawText User's message
     * @param newIntentId ID of the newly persisted intent
     * @return true if processed as follow-up, false if should be treated as new intent
     */
    @Transactional
    public boolean processAsFollowUpIfApplicable(String userId, String channel, String rawText, Long newIntentId) {
        Optional<UserIntentInboxEntity> pendingIntent = findPendingFollowUp(userId);
        
        if (pendingIntent.isEmpty()) {
            // No pending follow-up, treat as new intent
            log.debug("No pending follow-up for user {}, treating as new intent", userId);
            return false;
        }

        UserIntentInboxEntity parentIntent = pendingIntent.get();
        UserIntentInboxEntity followUpIntent = intentRepository.findById(newIntentId)
                .orElseThrow(() -> new IllegalArgumentException("Follow-up intent not found: " + newIntentId));

        log.info("Correlating follow-up intent {} with parent intent {} for user {}", 
                newIntentId, parentIntent.getId(), userId);

        // Link follow-up to parent
        followUpIntent.setFollowupParentId(parentIntent.getId());
        intentRepository.save(followUpIntent);

        // Resume processing of parent intent with follow-up data
        // The parent intent transitions back to PROCESSING and incorporates the follow-up
        resumeParentIntentProcessing(parentIntent, followUpIntent);

        return true;
    }

    /**
     * Resume processing of parent intent with follow-up data.
     * 
     * This method:
     * 1. Transitions parent from NEEDS_INPUT back to RECEIVED
     * 2. Appends follow-up context (for orchestrator to use)
     * 3. Triggers async reprocessing
     * 4. Marks follow-up as PROCESSED (it's incorporated into parent)
     * 
     * @param parentIntent Parent intent that needs input
     * @param followUpIntent Follow-up intent with additional data
     */
    @Transactional
    public void resumeParentIntentProcessing(UserIntentInboxEntity parentIntent, UserIntentInboxEntity followUpIntent) {
        log.info("Resuming processing of parent intent {} with follow-up {}", 
                parentIntent.getId(), followUpIntent.getId());

        // Transition parent back to RECEIVED for reprocessing
        parentIntent.setStatus(IntentStatus.RECEIVED);
        
        // Append follow-up text to parent's context
        // The orchestrator will use this to continue the conversation
        String combinedContext = buildCombinedContext(parentIntent, followUpIntent);
        parentIntent.setStatusReason("Resume with follow-up: " + followUpIntent.getRawText());
        
        intentRepository.save(parentIntent);

        // Mark follow-up as processed (it's now incorporated into parent)
        followUpIntent.markAsProcessed();
        followUpIntent.setStatusReason("Incorporated into parent intent " + parentIntent.getId());
        intentRepository.save(followUpIntent);

        // Trigger async reprocessing of parent
        processingEngine.processIntent(parentIntent.getId());
    }

    /**
     * Build combined context for parent + follow-up.
     * 
     * This could be enhanced to pass structured data to the orchestrator.
     * For now, we rely on the orchestrator's conversation context.
     * 
     * @param parent Parent intent
     * @param followUp Follow-up intent
     * @return Combined context string
     */
    private String buildCombinedContext(UserIntentInboxEntity parent, UserIntentInboxEntity followUp) {
        return String.format("Original: %s | Follow-up: %s", 
                parent.getRawText(), 
                followUp.getRawText());
    }

    /**
     * Validate that only one NEEDS_INPUT intent exists per user.
     * 
     * This is a system invariant check - should always be true.
     * If violated, indicates a bug in state management.
     * 
     * @param userId User identifier
     * @throws IllegalStateException if invariant violated
     */
    public void validateSinglePendingFollowUp(String userId) {
        long count = intentRepository.countByUserIdAndStatus(userId, IntentStatus.NEEDS_INPUT);
        if (count > 1) {
            throw new IllegalStateException(
                    "Invariant violated: User " + userId + " has " + count + 
                    " NEEDS_INPUT intents. Should have at most 1.");
        }
    }
}
