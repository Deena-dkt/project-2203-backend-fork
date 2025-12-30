package com.apps.deen_sa.conversation;

import com.apps.deen_sa.core.intent.FollowUpCorrelationService;
import com.apps.deen_sa.core.intent.IntentInboxService;
import com.apps.deen_sa.core.intent.IntentProcessingEngine;
import com.apps.deen_sa.core.intent.UserIntentInboxEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Log4j2
public class WhatsAppMessageProcessor {

    private final IntentInboxService intentInboxService;
    private final IntentProcessingEngine processingEngine;
    private final FollowUpCorrelationService followUpCorrelationService;
    private final WhatsAppReplySender replySender;

    /**
     * Phase 3: Async Processing with Follow-Up Support
     * 
     * This method:
     * 1. Persists intent immediately (Phase 1)
     * 2. Checks for pending follow-up (Phase 3)
     * 3. Routes to follow-up handler OR triggers new processing (Phase 2)
     * 4. Returns immediately for webhook acknowledgment
     * 
     * Processing is fully decoupled from ingestion.
     * Follow-up correlation is deterministic and async.
     */
    @Async("whatsappExecutor")
    public void processIncomingMessage(String from, String text) {

        try {
            // Phase 1: Persist intent BEFORE processing
            log.info("Received message - {} from {}", text, from);
            UserIntentInboxEntity intent = intentInboxService.persistIntent(from, "WHATSAPP", text);
            log.info("Intent persisted to inbox: id={}, correlation_id={}", intent.getId(), intent.getCorrelationId());
            
            // Phase 3: Check if this is a follow-up to a pending intent
            boolean isFollowUp = followUpCorrelationService.processAsFollowUpIfApplicable(
                    from, "WHATSAPP", text, intent.getId());
            
            if (isFollowUp) {
                log.info("Message from {} processed as follow-up to pending intent", from);
                // Follow-up correlation service handles the rest
                return;
            }
            
            // Phase 2: Trigger async processing for new intent
            // Processing happens separately - no inline handler execution
            processingEngine.processIntent(intent.getId());

        } catch (Exception e) {
            log.error("Error processing message from {}: {}", from, e.getMessage(), e);
            // Never let async failure kill future messages
            replySender.sendTextReply(
                    from,
                    "Something went wrong. Please try again."
            );
        }
    }
}
