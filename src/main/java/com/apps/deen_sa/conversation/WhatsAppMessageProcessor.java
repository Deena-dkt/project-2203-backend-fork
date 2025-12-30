package com.apps.deen_sa.conversation;

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
    private final WhatsAppReplySender replySender;

    /**
     * Phase 2: Async Processing with Intent Staging
     * 
     * This method:
     * 1. Persists intent immediately (Phase 1)
     * 2. Triggers async processing (Phase 2)
     * 3. Returns immediately for webhook acknowledgment
     * 
     * Processing is fully decoupled from ingestion.
     */
    @Async("whatsappExecutor")
    public void processIncomingMessage(String from, String text) {

        try {
            // Phase 1: Persist intent BEFORE processing
            log.info("Received message - {} from {}", text, from);
            UserIntentInboxEntity intent = intentInboxService.persistIntent(from, "WHATSAPP", text);
            log.info("Intent persisted to inbox: id={}, correlation_id={}", intent.getId(), intent.getCorrelationId());
            
            // Phase 2: Trigger async processing
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
