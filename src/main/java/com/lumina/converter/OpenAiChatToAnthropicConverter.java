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

@Slf4j
@Component
public class OpenAiChatToAnthropicConverter implements ProtocolConverter {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public ProtocolType sourceType() {
        return ProtocolType.OPENAI_CHAT;
    }

    @Override
    public ProtocolType targetType() {
        return ProtocolType.ANTHROPIC;
    }

    @Override
    public ObjectNode convertRequest(ObjectNode request) {
        ObjectNode result = mapper.createObjectNode();

        if (request.has("model")) {
            result.set("model", request.get("model"));
        }

        if (request.has("messages")) {
            ArrayNode messages = (ArrayNode) request.get("messages");
            ArrayNode anthropicMessages = mapper.createArrayNode();
            StringBuilder systemText = new StringBuilder();

            for (JsonNode msg : messages) {
                String role = msg.has("role") ? msg.get("role").asText() : "";
                if ("system".equals(role)) {
                    if (systemText.length() > 0) systemText.append("\n");
                    systemText.append(getContentText(msg));
                } else {
                    ObjectNode anthropicMsg = mapper.createObjectNode();
                    anthropicMsg.put("role", role);
                    if (msg.has("content") && msg.get("content").isArray()) {
                        anthropicMsg.set("content", msg.get("content"));
                    } else {
                        anthropicMsg.put("content", getContentText(msg));
                    }
                    anthropicMessages.add(anthropicMsg);
                }
            }

            if (systemText.length() > 0) {
                result.put("system", systemText.toString());
            }
            result.set("messages", anthropicMessages);
        }

        if (request.has("max_tokens")) {
            result.set("max_tokens", request.get("max_tokens"));
        } else {
            result.put("max_tokens", 4096);
        }

        if (request.has("temperature")) result.set("temperature", request.get("temperature"));
        if (request.has("top_p")) result.set("top_p", request.get("top_p"));
        if (request.has("stream")) result.set("stream", request.get("stream"));
        if (request.has("stop")) result.set("stop_sequences", request.get("stop"));

        return result;
    }

    @Override
    public ObjectNode convertResponse(ObjectNode response) {
        return convertAnthropicResponseToOpenAiChat(response);
    }

    @Override
    public Flux<ServerSentEvent<String>> convertStreamResponse(Flux<ServerSentEvent<String>> upstream) {
        return convertAnthropicStreamToOpenAiChat(upstream);
    }

    private ObjectNode convertAnthropicResponseToOpenAiChat(ObjectNode response) {
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

        if (response.has("content") && response.get("content").isArray()) {
            StringBuilder text = new StringBuilder();
            for (JsonNode block : response.get("content")) {
                if (block.has("text")) {
                    text.append(block.get("text").asText());
                }
            }
            message.put("content", text.toString());
        }
        choice.set("message", message);

        String stopReason = response.has("stop_reason") ? response.get("stop_reason").asText() : "stop";
        choice.put("finish_reason", mapStopReason(stopReason));
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

    private Flux<ServerSentEvent<String>> convertAnthropicStreamToOpenAiChat(Flux<ServerSentEvent<String>> upstream) {
        String chatId = "chatcmpl-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        AtomicBoolean sentRole = new AtomicBoolean(false);

        return upstream.flatMapIterable(event -> {
            String data = event.data();
            if (data == null || data.isBlank()) return java.util.Collections.<ServerSentEvent<String>>emptyList();

            try {
                JsonNode node = mapper.readTree(data);
                String type = node.has("type") ? node.get("type").asText() : "";

                return switch (type) {
                    case "message_start" -> {
                        sentRole.set(true);
                        String model = "";
                        if (node.has("message") && node.get("message").has("model")) {
                            model = node.get("message").get("model").asText();
                        }
                        ObjectNode chunk = buildStreamChunk(chatId, model, "assistant", null, null);
                        yield java.util.List.of(ServerSentEvent.<String>builder().data(chunk.toString()).build());
                    }
                    case "content_block_delta" -> {
                        String text = "";
                        if (node.has("delta") && node.get("delta").has("text")) {
                            text = node.get("delta").get("text").asText();
                        }
                        ObjectNode chunk = buildStreamChunk(chatId, null, null, text, null);
                        yield java.util.List.of(ServerSentEvent.<String>builder().data(chunk.toString()).build());
                    }
                    case "message_delta" -> {
                        String stopReason = null;
                        if (node.has("delta") && node.get("delta").has("stop_reason")) {
                            stopReason = mapStopReason(node.get("delta").get("stop_reason").asText());
                        }
                        ObjectNode chunk = buildStreamChunk(chatId, null, null, null, stopReason);
                        java.util.List<ServerSentEvent<String>> events = new java.util.ArrayList<>();
                        events.add(ServerSentEvent.<String>builder().data(chunk.toString()).build());
                        if (node.has("usage")) {
                            ObjectNode usageChunk = buildUsageChunk(chatId, node.get("usage"));
                            events.add(ServerSentEvent.<String>builder().data(usageChunk.toString()).build());
                        }
                        yield events;
                    }
                    case "message_stop" -> java.util.List.of(
                            ServerSentEvent.<String>builder().data("[DONE]").build()
                    );
                    default -> java.util.Collections.<ServerSentEvent<String>>emptyList();
                };
            } catch (Exception e) {
                log.debug("Failed to parse Anthropic stream event: {}", data);
                return java.util.Collections.<ServerSentEvent<String>>emptyList();
            }
        });
    }

    private ObjectNode buildStreamChunk(String id, String model, String role, String content, String finishReason) {
        ObjectNode chunk = mapper.createObjectNode();
        chunk.put("id", id);
        chunk.put("object", "chat.completion.chunk");
        chunk.put("created", System.currentTimeMillis() / 1000);
        if (model != null) chunk.put("model", model);

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

    private ObjectNode buildUsageChunk(String id, JsonNode usage) {
        ObjectNode chunk = mapper.createObjectNode();
        chunk.put("id", id);
        chunk.put("object", "chat.completion.chunk");
        ObjectNode openAiUsage = mapper.createObjectNode();
        openAiUsage.put("prompt_tokens", usage.has("input_tokens") ? usage.get("input_tokens").asInt() : 0);
        openAiUsage.put("completion_tokens", usage.has("output_tokens") ? usage.get("output_tokens").asInt() : 0);
        openAiUsage.put("total_tokens",
                (usage.has("input_tokens") ? usage.get("input_tokens").asInt() : 0) +
                (usage.has("output_tokens") ? usage.get("output_tokens").asInt() : 0));
        chunk.set("usage", openAiUsage);
        return chunk;
    }

    private String getContentText(JsonNode msg) {
        if (!msg.has("content")) return "";
        JsonNode content = msg.get("content");
        if (content.isTextual()) return content.asText();
        if (content.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode part : content) {
                if (part.has("text")) sb.append(part.get("text").asText());
            }
            return sb.toString();
        }
        return content.asText();
    }

    private String mapStopReason(String anthropicReason) {
        if (anthropicReason == null) return "stop";
        return switch (anthropicReason) {
            case "end_turn" -> "stop";
            case "max_tokens" -> "length";
            case "stop_sequence" -> "stop";
            default -> "stop";
        };
    }
}
