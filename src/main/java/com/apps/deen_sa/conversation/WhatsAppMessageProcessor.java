package com.apps.deen_sa.conversation;

import com.apps.deen_sa.core.intent.ChannelAgnosticIntentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Log4j2
public class WhatsAppMessageProcessor {

    private final ChannelAgnosticIntentService intentService;
    private final WhatsAppReplySender replySender;

    /**
     * Phase 4: Channel-Independent Processing
     * 
     * WhatsApp uses message-based interaction:
     * - Messages arrive via webhook
     * - Processing handled by channel-agnostic service
     * - Replies sent via WhatsApp API
     * 
     * The core intent processing is channel-independent.
     */
    @Async("whatsappExecutor")
    public void processIncomingMessage(String from, String text) {

        try {
            log.info("Received WhatsApp message from {}: '{}'", from, text);
            
            // Use channel-agnostic service
            // Same logic as UI/Mobile, just different delivery mechanism
            intentService.ingestIntent(from, "WHATSAPP", text);
            
            // WhatsApp-specific: No immediate reply needed
            // User will receive response when processing completes
            // (handled by separate notification mechanism, not implemented yet)

        } catch (Exception e) {
            log.error("Error processing WhatsApp message from {}: {}", from, e.getMessage(), e);
            // Never let async failure kill future messages
            replySender.sendTextReply(
                    from,
                    "Something went wrong. Please try again."
            );
        }
    }
}
