package com.lumina.converter;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

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
        ObjectNode chatRequest = anthropicToChat.convertRequest(request);
        return chatToResponses.convertRequest(chatRequest);
    }

    @Override
    public ObjectNode convertResponse(ObjectNode response) {
        ObjectNode chatResponse = chatToResponses.convertResponse(response);
        return anthropicToChat.convertResponse(chatResponse);
    }

    @Override
    public Flux<ServerSentEvent<String>> convertStreamResponse(Flux<ServerSentEvent<String>> upstream) {
        Flux<ServerSentEvent<String>> chatStream = chatToResponses.convertStreamResponse(upstream);
        return anthropicToChat.convertStreamResponse(chatStream);
    }
}
