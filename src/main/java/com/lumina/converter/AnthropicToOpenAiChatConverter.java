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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class AnthropicToOpenAiChatConverter implements ProtocolConverter {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public ProtocolType sourceType() {
        return ProtocolType.ANTHROPIC;
    }

    @Override
    public ProtocolType targetType() {
        return ProtocolType.OPENAI_CHAT;
    }

    @Override
    public ObjectNode convertRequest(ObjectNode request) {
        ObjectNode result = mapper.createObjectNode();

        if (request.has("model")) result.set("model", request.get("model"));

        ArrayNode messages = mapper.createArrayNode();

        if (request.has("system")) {
            ObjectNode systemMsg = mapper.createObjectNode();
            systemMsg.put("role", "system");
            JsonNode system = request.get("system");
            if (system.isTextual()) {
                systemMsg.put("content", system.asText());
            } else if (system.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode block : system) {
                    if (block.has("text")) sb.append(block.get("text").asText());
                }
                systemMsg.put("content", sb.toString());
            }
            messages.add(systemMsg);
        }

        if (request.has("messages")) {
            for (JsonNode msg : request.get("messages")) {
                ObjectNode openAiMsg = mapper.createObjectNode();
                openAiMsg.put("role", msg.has("role") ? msg.get("role").asText() : "user");
                if (msg.has("content")) {
                    if (msg.get("content").isTextual()) {
                        openAiMsg.put("content", msg.get("content").asText());
                    } else if (msg.get("content").isArray()) {
                        openAiMsg.set("content", msg.get("content"));
                    }
                }
                messages.add(openAiMsg);
            }
        }
        result.set("messages", messages);

        if (request.has("max_tokens")) result.set("max_tokens", request.get("max_tokens"));
        if (request.has("temperature")) result.set("temperature", request.get("temperature"));
        if (request.has("top_p")) result.set("top_p", request.get("top_p"));
        if (request.has("stream")) result.set("stream", request.get("stream"));
        if (request.has("stop_sequences")) result.set("stop", request.get("stop_sequences"));

        return result;
    }

    @Override
    public ObjectNode convertResponse(ObjectNode response) {
        return convertOpenAiChatResponseToAnthropic(response);
    }

    @Override
    public Flux<ServerSentEvent<String>> convertStreamResponse(Flux<ServerSentEvent<String>> upstream) {
        return convertOpenAiChatStreamToAnthropic(upstream);
    }

    private ObjectNode convertOpenAiChatResponseToAnthropic(ObjectNode response) {
        ObjectNode result = mapper.createObjectNode();
        String id = response.has("id") ? response.get("id").asText() : "msg_" + UUID.randomUUID();
        result.put("id", id);
        result.put("type", "message");
        result.put("role", "assistant");
        if (response.has("model")) result.set("model", response.get("model"));

        ArrayNode content = mapper.createArrayNode();
        if (response.has("choices") && response.get("choices").isArray()) {
            for (JsonNode choice : response.get("choices")) {
                if (choice.has("message") && choice.get("message").has("content")) {
                    ObjectNode textBlock = mapper.createObjectNode();
                    textBlock.put("type", "text");
                    textBlock.put("text", choice.get("message").get("content").asText());
                    content.add(textBlock);
                }
            }
        }
        result.set("content", content);

        String finishReason = "end_turn";
        if (response.has("choices") && response.get("choices").isArray() && response.get("choices").size() > 0) {
            JsonNode firstChoice = response.get("choices").get(0);
            if (firstChoice.has("finish_reason")) {
                finishReason = mapFinishReason(firstChoice.get("finish_reason").asText());
            }
        }
        result.put("stop_reason", finishReason);

        if (response.has("usage")) {
            JsonNode usage = response.get("usage");
            ObjectNode anthropicUsage = mapper.createObjectNode();
            anthropicUsage.put("input_tokens", usage.has("prompt_tokens") ? usage.get("prompt_tokens").asInt() : 0);
            anthropicUsage.put("output_tokens", usage.has("completion_tokens") ? usage.get("completion_tokens").asInt() : 0);
            result.set("usage", anthropicUsage);
        }

        return result;
    }

    private Flux<ServerSentEvent<String>> convertOpenAiChatStreamToAnthropic(Flux<ServerSentEvent<String>> upstream) {
        AtomicBoolean started = new AtomicBoolean(false);
        AtomicInteger blockIndex = new AtomicInteger(0);
        String msgId = "msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);

        return upstream.flatMapIterable(event -> {
            String data = event.data();
            if (data == null || data.isBlank()) return java.util.Collections.<ServerSentEvent<String>>emptyList();
            if ("[DONE]".equals(data)) {
                return buildAnthropicStopEvents(msgId, blockIndex.get());
            }

            try {
                JsonNode node = mapper.readTree(data);
                java.util.List<ServerSentEvent<String>> events = new java.util.ArrayList<>();

                if (!started.getAndSet(true)) {
                    events.addAll(buildAnthropicStartEvents(msgId, node));
                }

                if (node.has("choices") && node.get("choices").isArray()) {
                    for (JsonNode choice : node.get("choices")) {
                        if (choice.has("delta")) {
                            JsonNode delta = choice.get("delta");
                            if (delta.has("content") && !delta.get("content").isNull()) {
                                ObjectNode deltaEvent = mapper.createObjectNode();
                                deltaEvent.put("type", "content_block_delta");
                                deltaEvent.put("index", blockIndex.get());
                                ObjectNode deltaObj = mapper.createObjectNode();
                                deltaObj.put("type", "text_delta");
                                deltaObj.put("text", delta.get("content").asText());
                                deltaEvent.set("delta", deltaObj);
                                events.add(sse("content_block_delta", deltaEvent.toString()));
                            }
                        }
                        if (choice.has("finish_reason") && !choice.get("finish_reason").isNull()) {
                            ObjectNode msgDelta = mapper.createObjectNode();
                            msgDelta.put("type", "message_delta");
                            ObjectNode deltaObj = mapper.createObjectNode();
                            deltaObj.put("stop_reason", mapFinishReason(choice.get("finish_reason").asText()));
                            msgDelta.set("delta", deltaObj);
                            events.add(sse("message_delta", msgDelta.toString()));
                        }
                    }
                }
                return events;
            } catch (Exception e) {
                log.debug("Failed to parse OpenAI stream event: {}", data);
                return java.util.Collections.<ServerSentEvent<String>>emptyList();
            }
        });
    }

    private java.util.List<ServerSentEvent<String>> buildAnthropicStartEvents(String msgId, JsonNode firstChunk) {
        java.util.List<ServerSentEvent<String>> events = new java.util.ArrayList<>();

        ObjectNode messageStart = mapper.createObjectNode();
        messageStart.put("type", "message_start");
        ObjectNode message = mapper.createObjectNode();
        message.put("id", msgId);
        message.put("type", "message");
        message.put("role", "assistant");
        if (firstChunk.has("model")) message.put("model", firstChunk.get("model").asText());
        message.set("content", mapper.createArrayNode());
        message.putNull("stop_reason");
        // usage 是 message_start 必需字段
        ObjectNode usage = mapper.createObjectNode();
        usage.put("input_tokens", 0);
        usage.put("output_tokens", 0);
        message.set("usage", usage);
        messageStart.set("message", message);
        events.add(sse("message_start", messageStart.toString()));

        ObjectNode blockStart = mapper.createObjectNode();
        blockStart.put("type", "content_block_start");
        blockStart.put("index", 0);
        ObjectNode contentBlock = mapper.createObjectNode();
        contentBlock.put("type", "text");
        contentBlock.put("text", "");
        blockStart.set("content_block", contentBlock);
        events.add(sse("content_block_start", blockStart.toString()));

        return events;
    }

    private java.util.List<ServerSentEvent<String>> buildAnthropicStopEvents(String msgId, int blockIndex) {
        java.util.List<ServerSentEvent<String>> events = new java.util.ArrayList<>();

        ObjectNode blockStop = mapper.createObjectNode();
        blockStop.put("type", "content_block_stop");
        blockStop.put("index", blockIndex);
        events.add(sse("content_block_stop", blockStop.toString()));

        // message_delta 带 stop_reason 和 usage
        ObjectNode messageDelta = mapper.createObjectNode();
        messageDelta.put("type", "message_delta");
        ObjectNode delta = mapper.createObjectNode();
        delta.put("stop_reason", "end_turn");
        delta.putNull("stop_sequence");
        messageDelta.set("delta", delta);
        ObjectNode usage = mapper.createObjectNode();
        usage.put("output_tokens", 0);
        messageDelta.set("usage", usage);
        events.add(sse("message_delta", messageDelta.toString()));

        ObjectNode messageStop = mapper.createObjectNode();
        messageStop.put("type", "message_stop");
        events.add(sse("message_stop", messageStop.toString()));

        return events;
    }

    private ServerSentEvent<String> sse(String eventType, String data) {
        return ServerSentEvent.<String>builder().event(eventType).data(data).build();
    }

    private String mapFinishReason(String openAiReason) {
        if (openAiReason == null) return "end_turn";
        return switch (openAiReason) {
            case "stop" -> "end_turn";
            case "length" -> "max_tokens";
            case "content_filter" -> "end_turn";
            default -> "end_turn";
        };
    }
}
