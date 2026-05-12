package com.lumina.converter;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * OpenAI Responses → Anthropic Messages 协议转换器 (组合)
 *
 * 请求方向: Responses → Chat → Anthropic (两步转换)
 * 响应方向: Anthropic → Chat → Responses (两步转换)
 *
 * 流式: Anthropic stream → Chat stream → Responses stream
 */
@Slf4j
@Component
public class ResponsesToAnthropicConverter implements ProtocolConverter {

    private final ResponsesToOpenAiChatConverter responsesToChat;
    private final OpenAiChatToAnthropicConverter chatToAnthropic;

    public ResponsesToAnthropicConverter(ResponsesToOpenAiChatConverter responsesToChat,
                                         OpenAiChatToAnthropicConverter chatToAnthropic) {
        this.responsesToChat = responsesToChat;
        this.chatToAnthropic = chatToAnthropic;
    }

    @Override
    public ProtocolType sourceType() {
        return ProtocolType.OPENAI_RESPONSES;
    }

    @Override
    public ProtocolType targetType() {
        return ProtocolType.ANTHROPIC;
    }

    @Override
    public ObjectNode convertRequest(ObjectNode request) {
        // Responses → Chat → Anthropic
        ObjectNode chatRequest = responsesToChat.convertRequest(request);
        return chatToAnthropic.convertRequest(chatRequest);
    }

    @Override
    public ObjectNode convertResponse(ObjectNode response) {
        // Anthropic response → Chat response → Responses response
        ObjectNode chatResponse = chatToAnthropic.convertResponse(response);
        return responsesToChat.convertResponse(chatResponse);
    }

    @Override
    public Flux<ServerSentEvent<String>> convertStreamResponse(Flux<ServerSentEvent<String>> upstream) {
        // Anthropic stream → Chat stream → Responses stream
        // 第一步: Anthropic SSE events → OpenAI Chat SSE events
        Flux<ServerSentEvent<String>> chatStream = chatToAnthropic.convertStreamResponse(upstream);
        // 第二步: OpenAI Chat SSE events → Responses SSE events
        return responsesToChat.convertStreamResponse(chatStream);
    }
}
