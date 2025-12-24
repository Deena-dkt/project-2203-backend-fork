package com.apps.deen_sa.whatsApp;

import com.apps.deen_sa.orchestrator.ConversationContext;
import com.apps.deen_sa.orchestrator.SpeechOrchestrator;
import com.apps.deen_sa.orchestrator.SpeechResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Log4j2
public class WhatsAppMessageProcessor {

    private final SpeechOrchestrator orchestrator;
    private final ConversationContext conversationContext;
    private final WhatsAppReplySender replySender;

    @Async("whatsappExecutor")
    public void processIncomingMessage(String from, String text) {

        try {
            log.info("Received message - {} from {}", from, text);
            SpeechResult result =
                    orchestrator.process(text, conversationContext);

            log.info("Processed message - {} from {} and reply is ready - {}", from, text, result.getMessage());

            if (result.getMessage() != null) {
                replySender.sendTextReply(from, result.getMessage());
            }

        } catch (Exception e) {
            // Never let async failure kill future messages
            replySender.sendTextReply(
                    from,
                    "Something went wrong. Please try again."
            );
        }
    }
}
