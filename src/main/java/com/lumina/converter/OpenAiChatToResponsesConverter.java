package com.lumina.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * OpenAI Chat Completions → OpenAI Responses API 协议转换器
 *
 * 请求方向: 客户端发 OpenAI Chat 格式，转为 Responses 格式发给上游
 * 响应方向: 上游返回 Responses 格式，转为 OpenAI Chat 格式返回给客户端
 */
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

    // ==================== 请求转换: OpenAI Chat → Responses ====================

    @Override
    public ObjectNode convertRequest(ObjectNode request) {
        ObjectNode result = mapper.createObjectNode();
        if (request.has("model")) result.set("model", request.get("model"));

        ArrayNode input = mapper.createArrayNode();

        if (request.has("messages")) {
            for (JsonNode msg : request.get("messages")) {
                String role = msg.has("role") ? msg.get("role").asText() : "user";
                switch (role) {
                    case "system", "developer" -> {
                        // system → instructions 或 input item
                        if (!result.has("instructions")) {
                            result.put("instructions", getContentText(msg));
                        } else {
                            // 多个 system 消息追加到 instructions
                            String existing = result.get("instructions").asText();
                            result.put("instructions", existing + "\n" + getContentText(msg));
                        }
                    }
                    case "assistant" -> {
                        // assistant with tool_calls → function_call items
                        if (msg.has("tool_calls") && msg.get("tool_calls").isArray()) {
                            for (JsonNode tc : msg.get("tool_calls")) {
                                ObjectNode fcItem = mapper.createObjectNode();
                                fcItem.put("type", "function_call");
                                fcItem.put("call_id", tc.has("id") ? tc.get("id").asText() : "");
                                if (tc.has("function")) {
                                    JsonNode func = tc.get("function");
                                    fcItem.put("name", func.has("name") ? func.get("name").asText() : "");
                                    fcItem.put("arguments", func.has("arguments") ? func.get("arguments").asText() : "");
                                }
                                input.add(fcItem);
                            }
                        }
                        // assistant text content → message item
                        String text = getContentText(msg);
                        if (!text.isEmpty()) {
                            ObjectNode item = mapper.createObjectNode();
                            item.put("type", "message");
                            item.put("role", "assistant");
                            ArrayNode content = mapper.createArrayNode();
                            ObjectNode textPart = mapper.createObjectNode();
                            textPart.put("type", "output_text");
                            textPart.put("text", text);
                            content.add(textPart);
                            item.set("content", content);
                            input.add(item);
                        }
                    }
                    case "tool" -> {
                        // tool → function_call_output
                        ObjectNode fcoItem = mapper.createObjectNode();
                        fcoItem.put("type", "function_call_output");
                        fcoItem.put("call_id", msg.has("tool_call_id") ? msg.get("tool_call_id").asText() : "");
                        fcoItem.put("output", getContentText(msg));
                        input.add(fcoItem);
                    }
                    default -> {
                        // user → message item
                        ObjectNode item = mapper.createObjectNode();
                        item.put("type", "message");
                        item.put("role", "user");
                        ArrayNode content = mapper.createArrayNode();
                        ObjectNode textPart = mapper.createObjectNode();
                        textPart.put("type", "input_text");
                        textPart.put("text", getContentText(msg));
                        content.add(textPart);
                        item.set("content", content);
                        input.add(item);
                    }
                }
            }
        }

        result.set("input", input);

        if (request.has("max_tokens")) {
            result.set("max_output_tokens", request.get("max_tokens"));
        } else if (request.has("max_completion_tokens")) {
            result.set("max_output_tokens", request.get("max_completion_tokens"));
        }
        if (request.has("temperature")) result.set("temperature", request.get("temperature"));
        if (request.has("top_p")) result.set("top_p", request.get("top_p"));
        if (request.has("stream")) result.set("stream", request.get("stream"));

        // tools 转换: Chat 嵌套格式 → Responses 扁平格式
        if (request.has("tools") && request.get("tools").isArray()) {
            ArrayNode responsesTools = mapper.createArrayNode();
            for (JsonNode tool : request.get("tools")) {
                if (tool.has("type") && "function".equals(tool.get("type").asText()) && tool.has("function")) {
                    JsonNode func = tool.get("function");
                    ObjectNode rTool = mapper.createObjectNode();
                    rTool.put("type", "function");
                    rTool.put("name", func.has("name") ? func.get("name").asText() : "");
                    if (func.has("description")) rTool.put("description", func.get("description").asText());
                    if (func.has("parameters")) rTool.set("parameters", func.get("parameters"));
                    responsesTools.add(rTool);
                }
            }
            if (responsesTools.size() > 0) {
                result.set("tools", responsesTools);
            }
        }

        if (request.has("tool_choice")) result.set("tool_choice", request.get("tool_choice"));

        log.debug("Chat→Responses converted request: {}", result);
        return result;
    }

    // ==================== 响应转换: Responses → OpenAI Chat ====================

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

        StringBuilder textContent = new StringBuilder();
        ArrayNode toolCalls = mapper.createArrayNode();
        int toolCallIndex = 0;

        if (response.has("output") && response.get("output").isArray()) {
            for (JsonNode outputItem : response.get("output")) {
                String itemType = outputItem.has("type") ? outputItem.get("type").asText() : "";
                if ("message".equals(itemType) && outputItem.has("content") && outputItem.get("content").isArray()) {
                    for (JsonNode contentPart : outputItem.get("content")) {
                        if (contentPart.has("text")) {
                            textContent.append(contentPart.get("text").asText());
                        }
                    }
                } else if ("function_call".equals(itemType)) {
                    ObjectNode tc = mapper.createObjectNode();
                    tc.put("id", outputItem.has("call_id") ? outputItem.get("call_id").asText() : "call_" + UUID.randomUUID());
                    tc.put("type", "function");
                    ObjectNode func = mapper.createObjectNode();
                    func.put("name", outputItem.has("name") ? outputItem.get("name").asText() : "");
                    func.put("arguments", outputItem.has("arguments") ? outputItem.get("arguments").asText() : "{}");
                    tc.set("function", func);
                    tc.put("index", toolCallIndex++);
                    toolCalls.add(tc);
                }
            }
        }

        if (textContent.length() > 0) {
            message.put("content", textContent.toString());
        } else {
            message.putNull("content");
        }
        if (toolCalls.size() > 0) {
            message.set("tool_calls", toolCalls);
        }

        choice.set("message", message);
        choice.put("finish_reason", toolCalls.size() > 0 ? "tool_calls" : "stop");
        choices.add(choice);
        result.set("choices", choices);

        // usage
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

    // ==================== 流式转换: Responses Stream → OpenAI Chat Stream ====================

    @Override
    public Flux<ServerSentEvent<String>> convertStreamResponse(Flux<ServerSentEvent<String>> upstream) {
        String chatId = "chatcmpl-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        AtomicBoolean sentRole = new AtomicBoolean(false);
        // 跟踪 function_call 的 tool_calls index
        ConcurrentHashMap<String, Integer> itemIdToToolCallIndex = new ConcurrentHashMap<>();
        AtomicInteger toolCallCounter = new AtomicInteger(0);

        return upstream.flatMapIterable(event -> {
            String data = event.data();
            if (data == null || data.isBlank()) return Collections.<ServerSentEvent<String>>emptyList();

            try {
                JsonNode node = mapper.readTree(data);
                String type = node.has("type") ? node.get("type").asText() : "";

                return switch (type) {
                    case "response.output_text.delta", "response.content_part.delta" -> {
                        List<ServerSentEvent<String>> events = new ArrayList<>();
                        if (!sentRole.getAndSet(true)) {
                            ObjectNode roleChunk = buildChatStreamChunk(chatId, null, "assistant", null, null, null);
                            events.add(ServerSentEvent.<String>builder().data(roleChunk.toString()).build());
                        }
                        String text = node.has("delta") ? node.get("delta").asText() : "";
                        ObjectNode chunk = buildChatStreamChunk(chatId, null, null, text, null, null);
                        events.add(ServerSentEvent.<String>builder().data(chunk.toString()).build());
                        yield events;
                    }
                    case "response.output_item.added" -> {
                        // 检查是否是 function_call item
                        if (node.has("item") && node.get("item").has("type")) {
                            String itemType = node.get("item").get("type").asText();
                            if ("function_call".equals(itemType)) {
                                JsonNode item = node.get("item");
                                String itemId = item.has("id") ? item.get("id").asText() : "";
                                String callId = item.has("call_id") ? item.get("call_id").asText() : itemId;
                                String name = item.has("name") ? item.get("name").asText() : "";
                                int tcIdx = toolCallCounter.getAndIncrement();
                                itemIdToToolCallIndex.put(itemId, tcIdx);

                                List<ServerSentEvent<String>> events = new ArrayList<>();
                                if (!sentRole.getAndSet(true)) {
                                    ObjectNode roleChunk = buildChatStreamChunk(chatId, null, "assistant", null, null, null);
                                    events.add(ServerSentEvent.<String>builder().data(roleChunk.toString()).build());
                                }

                                // 发送 tool_call start
                                ObjectNode chunk = buildToolCallStartChunk(chatId, tcIdx, callId, name);
                                events.add(ServerSentEvent.<String>builder().data(chunk.toString()).build());
                                yield events;
                            }
                        }
                        yield Collections.<ServerSentEvent<String>>emptyList();
                    }
                    case "response.function_call_arguments.delta" -> {
                        String itemId = node.has("item_id") ? node.get("item_id").asText() : "";
                        String argDelta = node.has("delta") ? node.get("delta").asText() : "";
                        int tcIdx = itemIdToToolCallIndex.getOrDefault(itemId, 0);

                        ObjectNode chunk = buildToolCallDeltaChunk(chatId, tcIdx, argDelta);
                        yield List.of(ServerSentEvent.<String>builder().data(chunk.toString()).build());
                    }
                    case "response.completed", "response.done" -> {
                        // 发送 finish_reason + [DONE]
                        List<ServerSentEvent<String>> events = new ArrayList<>();
                        String finishReason = toolCallCounter.get() > 0 ? "tool_calls" : "stop";
                        ObjectNode finishChunk = buildChatStreamChunk(chatId, null, null, null, finishReason, null);
                        events.add(ServerSentEvent.<String>builder().data(finishChunk.toString()).build());
                        events.add(ServerSentEvent.<String>builder().data("[DONE]").build());
                        yield events;
                    }
                    default -> Collections.<ServerSentEvent<String>>emptyList();
                };
            } catch (Exception e) {
                log.debug("Failed to parse Responses stream event: {}", data);
                return Collections.<ServerSentEvent<String>>emptyList();
            }
        });
    }

    private ObjectNode buildChatStreamChunk(String id, String model, String role, String content, String finishReason, ArrayNode toolCalls) {
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
        if (toolCalls != null) delta.set("tool_calls", toolCalls);
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

    private ObjectNode buildToolCallStartChunk(String chatId, int index, String id, String name) {
        ObjectNode chunk = mapper.createObjectNode();
        chunk.put("id", chatId);
        chunk.put("object", "chat.completion.chunk");
        chunk.put("created", System.currentTimeMillis() / 1000);

        ArrayNode choices = mapper.createArrayNode();
        ObjectNode choice = mapper.createObjectNode();
        choice.put("index", 0);

        ObjectNode delta = mapper.createObjectNode();
        ArrayNode toolCalls = mapper.createArrayNode();
        ObjectNode tc = mapper.createObjectNode();
        tc.put("index", index);
        tc.put("id", id);
        tc.put("type", "function");
        ObjectNode func = mapper.createObjectNode();
        func.put("name", name);
        func.put("arguments", "");
        tc.set("function", func);
        toolCalls.add(tc);
        delta.set("tool_calls", toolCalls);

        choice.set("delta", delta);
        choice.putNull("finish_reason");
        choices.add(choice);
        chunk.set("choices", choices);
        return chunk;
    }

    private ObjectNode buildToolCallDeltaChunk(String chatId, int index, String argumentsDelta) {
        ObjectNode chunk = mapper.createObjectNode();
        chunk.put("id", chatId);
        chunk.put("object", "chat.completion.chunk");
        chunk.put("created", System.currentTimeMillis() / 1000);

        ArrayNode choices = mapper.createArrayNode();
        ObjectNode choice = mapper.createObjectNode();
        choice.put("index", 0);

        ObjectNode delta = mapper.createObjectNode();
        ArrayNode toolCalls = mapper.createArrayNode();
        ObjectNode tc = mapper.createObjectNode();
        tc.put("index", index);
        ObjectNode func = mapper.createObjectNode();
        func.put("arguments", argumentsDelta);
        tc.set("function", func);
        toolCalls.add(tc);
        delta.set("tool_calls", toolCalls);

        choice.set("delta", delta);
        choice.putNull("finish_reason");
        choices.add(choice);
        chunk.set("choices", choices);
        return chunk;
    }

    private String getContentText(JsonNode msg) {
        if (!msg.has("content")) return "";
        JsonNode content = msg.get("content");
        if (content.isTextual()) return content.asText();
        if (content.isNull()) return "";
        if (content.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode part : content) {
                if (part.has("text")) sb.append(part.get("text").asText());
            }
            return sb.toString();
        }
        return content.asText();
    }
}
