package com.apps.deen_sa.controller;

import com.apps.deen_sa.dto.SpeechInput;
import com.apps.deen_sa.orchestrator.ConversationContext;
import com.apps.deen_sa.orchestrator.SpeechOrchestrator;
import com.apps.deen_sa.orchestrator.SpeechResult;
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

