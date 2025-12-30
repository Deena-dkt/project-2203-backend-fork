package com.apps.deen_sa.conversation;

import com.apps.deen_sa.core.intent.ChannelAgnosticIntentService;
import com.apps.deen_sa.core.intent.UserIntentInboxEntity;
import com.apps.deen_sa.dto.SpeechInput;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.web.bind.annotation.*;

/**
 * Legacy Speech Controller
 * 
 * Note: This is the old synchronous API.
 * For new implementations, use IntentApiController instead.
 * 
 * This controller is kept for backward compatibility but now uses
 * the channel-agnostic intent service internally.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Log4j2
public class SpeechController {

    private final ChannelAgnosticIntentService intentService;
    private final SpeechOrchestrator orchestrator;
    private final ConversationContext conversationContext;

    /**
     * Legacy synchronous processing endpoint.
     * 
     * @deprecated Use IntentApiController for new implementations
     */
    @PostMapping("/process")
    public SpeechResult processSpeech(@RequestBody SpeechInput request) {

        log.info("Message is about to be processed (legacy sync API) - {}", request.getText());
        
        // For backward compatibility, still use synchronous processing
        // But log that this is deprecated
        log.warn("Using legacy synchronous API. Consider migrating to /api/v1/intents");
        
        return orchestrator.process(
                request.getText(),
                conversationContext
        );
    }
}

