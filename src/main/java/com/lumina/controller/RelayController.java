package com.lumina.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lumina.service.RelayService;
import com.lumina.service.TokenCountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping
public class RelayController {

    @Autowired
    private RelayService relayService;

    @Autowired
    private TokenCountService tokenCountService;

    @GetMapping("/v1/models")
    public Mono<ResponseEntity<?>> models() {
        return relayService.models();
    }

    @PostMapping("/v1/messages")
    public Mono<ResponseEntity<?>> createMessage(
            @RequestBody ObjectNode params,
            @RequestParam Map<String, String> allParams,
            ServerWebExchange exchange) {
        String apiKey = exchange.getAttribute("API_KEY");
        return relayService.relay("anthropic_messages", params, allParams, apiKey);
    }


    @PostMapping(
            value = "/v1/chat/completions",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<ResponseEntity<?>> createChatCompletions(
            @RequestBody ObjectNode params,
            @RequestParam Map<String, String> allParams,
            ServerWebExchange exchange) {
        String apiKey = exchange.getAttribute("API_KEY");
        return relayService.relay("openai_chat_completions", params, allParams, apiKey);
    }

    @PostMapping(
            value = "/v1/responses",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<ResponseEntity<?>> createResponses(
            @RequestBody ObjectNode params,
            @RequestParam Map<String, String> allParams,
            ServerWebExchange exchange) {
        String apiKey = exchange.getAttribute("API_KEY");
        return relayService.relay("openai_responses", params, allParams, apiKey);
    }

    @PostMapping(
            value = "/v1beta/models/{modelAction}",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<ResponseEntity<?>> createModels(
            @PathVariable String modelAction,
            @RequestBody ObjectNode params,
            @RequestParam Map<String, String> allParams,
            ServerWebExchange exchange) {
        String apiKey = exchange.getAttribute("API_KEY");
        return relayService.relay("gemini_models", modelAction, params, allParams, apiKey);
    }

    @PostMapping(
            value = "/v1/messages/count_tokens",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<ResponseEntity<?>> countTokens(@RequestBody ObjectNode params) {
        String modelName = params.has("model") ? params.get("model").asText() : "gpt-4o";
        int tokens = tokenCountService.countTokens(modelName, params);
        return Mono.just(ResponseEntity.ok(Map.of("input_tokens", tokens)));
    }
}
