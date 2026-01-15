package com.lumina.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lumina.service.RelayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping
public class RelayController {

    @Autowired
    private RelayService relayService;

    @PostMapping("/v1/messages")
    public Mono<ResponseEntity<?>> createMessage(
            @RequestBody ObjectNode params,
            @RequestParam Map<String, String> allParams) {
        return relayService.relay("anthropic_messages", params, allParams);
    }


    @PostMapping(
            value = "/v1/chat/completions",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<ResponseEntity<?>> createChatCompletions(
            @RequestBody ObjectNode params,
            @RequestParam Map<String, String> allParams) {
        return relayService.relay("openai_chat_completions", params, allParams);
    }

    @PostMapping(
            value = "/v1/responses",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<ResponseEntity<?>> createResponses(
            @RequestBody ObjectNode params,
            @RequestParam Map<String, String> allParams) {
        return relayService.relay("openai_responses", params, allParams);
    }

    @PostMapping(
            value = "/v1beta/models/{modelAction}",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<ResponseEntity<?>> createModels(
            @PathVariable String modelAction,
            @RequestBody ObjectNode params,
            @RequestParam Map<String, String> allParams) {
        return relayService.relay("gemini_models", modelAction,params, allParams);
    }
}
