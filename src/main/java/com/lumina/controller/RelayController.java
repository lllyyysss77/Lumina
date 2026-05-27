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

import java.net.InetSocketAddress;
import java.util.Map;

@RestController
@RequestMapping
public class RelayController {

    @Autowired
    private RelayService relayService;

    @Autowired
    private TokenCountService tokenCountService;

    private String extractClientIp(ServerWebExchange exchange) {
        String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        String realIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (realIp != null && !realIp.isEmpty()) {
            return realIp;
        }
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
    }

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
        allParams.put("_lumina_request_ip", extractClientIp(exchange));
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
        allParams.put("_lumina_request_ip", extractClientIp(exchange));
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
        allParams.put("_lumina_request_ip", extractClientIp(exchange));
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
        allParams.put("_lumina_request_ip", extractClientIp(exchange));
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
