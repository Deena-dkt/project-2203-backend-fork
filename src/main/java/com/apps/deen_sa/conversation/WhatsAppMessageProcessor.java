package com.apps.deen_sa.conversation;

import com.apps.deen_sa.conversation.ConversationContext;
import com.apps.deen_sa.conversation.SpeechOrchestrator;
import com.apps.deen_sa.conversation.SpeechResult;
import com.apps.deen_sa.core.intent.IntentInboxService;
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
    private final SpeechOrchestrator orchestrator;
    private final ConversationContext conversationContext;
    private final WhatsAppReplySender replySender;

    @Async("whatsappExecutor")
    public void processIncomingMessage(String from, String text) {

        try {
            // Phase 1: Persist intent BEFORE processing
            log.info("Received message - {} from {}", text, from);
            UserIntentInboxEntity intent = intentInboxService.persistIntent(from, "WHATSAPP", text);
            log.info("Intent persisted to inbox: id={}, correlation_id={}", intent.getId(), intent.getCorrelationId());
            
            // Process the intent (existing flow)
            SpeechResult result = orchestrator.process(text, conversationContext);

            log.info("Processed message - {} from {} and reply is ready - {}", text, from, result.getMessage());

            if (result.getMessage() != null) {
                replySender.sendTextReply(from, result.getMessage());
            }

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
