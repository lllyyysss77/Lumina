package com.lumina.converter;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Anthropic Messages → OpenAI Responses 协议转换器 (组合)
 *
 * 请求方向: Anthropic → Chat → Responses (两步转换)
 * 响应方向: Responses → Chat → Anthropic (两步转换)
 *
 * 流式: Responses stream → Chat stream → Anthropic stream
 */
@Slf4j
@Component
public class AnthropicToResponsesConverter implements ProtocolConverter {

    private final AnthropicToOpenAiChatConverter anthropicToChat;
    private final OpenAiChatToResponsesConverter chatToResponses;

    public AnthropicToResponsesConverter(AnthropicToOpenAiChatConverter anthropicToChat,
                                          OpenAiChatToResponsesConverter chatToResponses) {
        this.anthropicToChat = anthropicToChat;
        this.chatToResponses = chatToResponses;
    }

    @Override
    public ProtocolType sourceType() {
        return ProtocolType.ANTHROPIC;
    }

    @Override
    public ProtocolType targetType() {
        return ProtocolType.OPENAI_RESPONSES;
    }

    @Override
    public ObjectNode convertRequest(ObjectNode request) {
        // Anthropic → Chat → Responses
        ObjectNode chatRequest = anthropicToChat.convertRequest(request);
        return chatToResponses.convertRequest(chatRequest);
    }

    @Override
    public ObjectNode convertResponse(ObjectNode response) {
        // Responses response → Chat response → Anthropic response
        ObjectNode chatResponse = chatToResponses.convertResponse(response);
        return anthropicToChat.convertResponse(chatResponse);
    }

    @Override
    public Flux<ServerSentEvent<String>> convertStreamResponse(Flux<ServerSentEvent<String>> upstream) {
        // Responses stream → Chat stream → Anthropic stream
        // 第一步: Responses SSE events → OpenAI Chat SSE events
        Flux<ServerSentEvent<String>> chatStream = chatToResponses.convertStreamResponse(upstream);
        // 第二步: OpenAI Chat SSE events → Anthropic SSE events
        return anthropicToChat.convertStreamResponse(chatStream);
    }
}
