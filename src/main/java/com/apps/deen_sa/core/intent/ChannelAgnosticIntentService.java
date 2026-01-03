package com.apps.deen_sa.core.intent;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

/**
 * Phase 4: Channel-Agnostic Intent Ingestion
 * 
 * Provides channel-independent intent ingestion and processing.
 * All channels (WhatsApp, UI, Mobile, API) use this service.
 * 
 * Key responsibilities:
 * - Persist incoming intents (channel-agnostic)
 * - Detect and handle follow-ups
 * - Trigger async processing
 * 
 * Invariants:
 * - Same intent behaves identically regardless of channel
 * - Core processing has no channel dependencies
 * - Channel-specific concerns (e.g., reply sending) handled by caller
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class ChannelAgnosticIntentService {

    private final IntentInboxService intentInboxService;
    private final IntentProcessingEngine processingEngine;
    private final FollowUpCorrelationService followUpCorrelationService;

    /**
     * Ingest a user intent from any channel.
     * 
     * This is the main entry point for all channels.
     * 
     * Flow:
     * 1. Persist intent (Phase 1)
     * 2. Check for follow-up correlation (Phase 3)
     * 3. Trigger async processing (Phase 2)
     * 
     * @param userId User identifier
     * @param channel Channel name (WHATSAPP, WEB, MOBILE, API)
     * @param rawText User's message/input
     * @return The persisted intent entity
     */
    public UserIntentInboxEntity ingestIntent(String userId, String channel, String rawText) {
        // Phase 1: Persist intent BEFORE processing
        log.info("Ingesting intent from user {} via channel {}: '{}'", userId, channel, rawText);
        UserIntentInboxEntity intent = intentInboxService.persistIntent(userId, channel, rawText);
        log.info("Intent persisted: id={}, correlation_id={}", intent.getId(), intent.getCorrelationId());
        
        // Phase 3: Check if this is a follow-up to a pending intent
        boolean isFollowUp = followUpCorrelationService.processAsFollowUpIfApplicable(
                userId, channel, rawText, intent.getId());
        
        if (isFollowUp) {
            log.info("Intent {} processed as follow-up to pending intent", intent.getId());
            // Follow-up correlation service handles resumption
            return intent;
        }
        
        // Phase 2: Trigger async processing for new intent
        log.info("Triggering async processing for new intent {}", intent.getId());
        processingEngine.processIntent(intent.getId());
        
        return intent;
    }

    /**
     * Get the current pending action for a user.
     * 
     * Used by UI/Mobile clients to check if user has unresolved follow-up.
     * 
     * @param userId User identifier
     * @return The pending NEEDS_INPUT intent, or null if none
     */
    public UserIntentInboxEntity getPendingAction(String userId) {
        return followUpCorrelationService.findPendingFollowUp(userId).orElse(null);
    }

    /**
     * Resume processing with user's response.
     * 
     * Used by UI/Mobile clients for state-based interaction.
     * 
     * @param userId User identifier
     * @param responseText User's response to pending action
     * @return The follow-up intent entity
     */
    public UserIntentInboxEntity resumeWithResponse(String userId, String channel, String responseText) {
        log.info("Resuming intent for user {} via channel {} with response: '{}'", 
                userId, channel, responseText);
        
        // Ingest the response as a normal intent
        // The follow-up correlation will handle linking it to the pending intent
        return ingestIntent(userId, channel, responseText);
    }
}
