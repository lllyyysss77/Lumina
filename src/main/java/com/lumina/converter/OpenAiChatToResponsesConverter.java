package com.lumina.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.UUID;

@Slf4j
@Component
public class OpenAiChatToResponsesConverter implements ProtocolConverter {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public ProtocolType sourceType() {
        return ProtocolType.OPENAI_CHAT;
    }

    @Override
    public ProtocolType targetType() {
        return ProtocolType.OPENAI_RESPONSES;
    }

    @Override
    public ObjectNode convertRequest(ObjectNode request) {
        ObjectNode result = mapper.createObjectNode();
        if (request.has("model")) result.set("model", request.get("model"));

        if (request.has("messages")) {
            result.set("input", request.get("messages"));
        }

        if (request.has("max_tokens")) {
            result.set("max_output_tokens", request.get("max_tokens"));
        }
        if (request.has("temperature")) result.set("temperature", request.get("temperature"));
        if (request.has("top_p")) result.set("top_p", request.get("top_p"));
        if (request.has("stream")) result.set("stream", request.get("stream"));

        return result;
    }

    @Override
    public ObjectNode convertResponse(ObjectNode response) {
        ObjectNode result = mapper.createObjectNode();
        String id = response.has("id") ? response.get("id").asText() : "chatcmpl-" + UUID.randomUUID();
        result.put("id", id);
        result.put("object", "chat.completion");
        result.put("created", System.currentTimeMillis() / 1000);
        if (response.has("model")) result.set("model", response.get("model"));

        ArrayNode choices = mapper.createArrayNode();
        ObjectNode choice = mapper.createObjectNode();
        choice.put("index", 0);

        ObjectNode message = mapper.createObjectNode();
        message.put("role", "assistant");

        String text = extractResponseText(response);
        message.put("content", text);
        choice.set("message", message);
        choice.put("finish_reason", "stop");
        choices.add(choice);
        result.set("choices", choices);

        if (response.has("usage")) {
            JsonNode usage = response.get("usage");
            ObjectNode openAiUsage = mapper.createObjectNode();
            openAiUsage.put("prompt_tokens", usage.has("input_tokens") ? usage.get("input_tokens").asInt() : 0);
            openAiUsage.put("completion_tokens", usage.has("output_tokens") ? usage.get("output_tokens").asInt() : 0);
            openAiUsage.put("total_tokens",
                    (usage.has("input_tokens") ? usage.get("input_tokens").asInt() : 0) +
                    (usage.has("output_tokens") ? usage.get("output_tokens").asInt() : 0));
            result.set("usage", openAiUsage);
        }

        return result;
    }

    @Override
    public Flux<ServerSentEvent<String>> convertStreamResponse(Flux<ServerSentEvent<String>> upstream) {
        String chatId = "chatcmpl-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);

        return upstream.flatMapIterable(event -> {
            String data = event.data();
            if (data == null || data.isBlank()) return java.util.Collections.<ServerSentEvent<String>>emptyList();

            try {
                JsonNode node = mapper.readTree(data);
                String type = node.has("type") ? node.get("type").asText() : "";

                if ("response.output_text.delta".equals(type) || "response.content_part.delta".equals(type)) {
                    String text = node.has("delta") ? node.get("delta").asText() : "";
                    ObjectNode chunk = buildChatStreamChunk(chatId, null, text, null);
                    return java.util.List.of(ServerSentEvent.<String>builder().data(chunk.toString()).build());
                } else if ("response.completed".equals(type) || "response.done".equals(type)) {
                    return java.util.List.of(ServerSentEvent.<String>builder().data("[DONE]").build());
                }

                return java.util.Collections.<ServerSentEvent<String>>emptyList();
            } catch (Exception e) {
                log.debug("Failed to parse Responses stream event: {}", data);
                return java.util.Collections.<ServerSentEvent<String>>emptyList();
            }
        });
    }

    private String extractResponseText(ObjectNode response) {
        if (response.has("output") && response.get("output").isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode outputItem : response.get("output")) {
                if (outputItem.has("content") && outputItem.get("content").isArray()) {
                    for (JsonNode contentPart : outputItem.get("content")) {
                        if (contentPart.has("text")) {
                            sb.append(contentPart.get("text").asText());
                        }
                    }
                }
            }
            return sb.toString();
        }
        return "";
    }

    private ObjectNode buildChatStreamChunk(String id, String role, String content, String finishReason) {
        ObjectNode chunk = mapper.createObjectNode();
        chunk.put("id", id);
        chunk.put("object", "chat.completion.chunk");
        chunk.put("created", System.currentTimeMillis() / 1000);

        ArrayNode choices = mapper.createArrayNode();
        ObjectNode choice = mapper.createObjectNode();
        choice.put("index", 0);

        ObjectNode delta = mapper.createObjectNode();
        if (role != null) delta.put("role", role);
        if (content != null) delta.put("content", content);
        choice.set("delta", delta);

        if (finishReason != null) {
            choice.put("finish_reason", finishReason);
        } else {
            choice.putNull("finish_reason");
        }
        choices.add(choice);
        chunk.set("choices", choices);
        return chunk;
    }
}
