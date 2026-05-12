package com.lumina.converter;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

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
        ObjectNode chatRequest = responsesToChat.convertRequest(request);
        return chatToAnthropic.convertRequest(chatRequest);
    }

    @Override
    public ObjectNode convertResponse(ObjectNode response) {
        ObjectNode chatResponse = chatToAnthropic.convertResponse(response);
        return responsesToChat.convertResponse(chatResponse);
    }

    @Override
    public Flux<ServerSentEvent<String>> convertStreamResponse(Flux<ServerSentEvent<String>> upstream) {
        Flux<ServerSentEvent<String>> chatStream = chatToAnthropic.convertStreamResponse(upstream);
        return responsesToChat.convertStreamResponse(chatStream);
    }
}
