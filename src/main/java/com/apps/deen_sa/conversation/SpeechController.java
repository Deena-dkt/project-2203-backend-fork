package com.apps.deen_sa.conversation;

import com.apps.deen_sa.dto.SpeechInput;
import com.apps.deen_sa.conversation.ConversationContext;
import com.apps.deen_sa.conversation.SpeechOrchestrator;
import com.apps.deen_sa.conversation.SpeechResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Log4j2
public class SpeechController {

    private final SpeechOrchestrator orchestrator;
    private final ConversationContext conversationContext;

    @PostMapping("/process")
    public SpeechResult processSpeech(@RequestBody SpeechInput request) {

        log.info("Message is about to be processed - {}", request.getText());
        return orchestrator.process(
                request.getText(),
                conversationContext
        );
    }
}

