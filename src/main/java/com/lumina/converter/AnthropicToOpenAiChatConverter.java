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
 * Anthropic Messages → OpenAI Chat Completions 协议转换器
 *
 * 请求方向: 客户端发 Anthropic 格式，转为 OpenAI Chat 格式发给上游
 * 响应方向: 上游返回 OpenAI Chat 格式，转为 Anthropic 格式返回给客户端
 */
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

    // ==================== 请求转换: Anthropic → OpenAI Chat ====================

    @Override
    public ObjectNode convertRequest(ObjectNode request) {
        ObjectNode result = mapper.createObjectNode();

        if (request.has("model")) result.set("model", request.get("model"));

        ArrayNode messages = mapper.createArrayNode();

        // system → system message
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

        // 转换 messages
        if (request.has("messages")) {
            for (JsonNode msg : request.get("messages")) {
                String role = msg.has("role") ? msg.get("role").asText() : "user";
                if ("assistant".equals(role)) {
                    convertAnthropicAssistantMessage(msg, messages);
                } else if ("user".equals(role)) {
                    convertAnthropicUserMessage(msg, messages);
                } else {
                    // 其他角色直接透传
                    ObjectNode openAiMsg = mapper.createObjectNode();
                    openAiMsg.put("role", role);
                    openAiMsg.put("content", getContentText(msg));
                    messages.add(openAiMsg);
                }
            }
        }
        result.set("messages", messages);

        if (request.has("max_tokens")) result.set("max_tokens", request.get("max_tokens"));
        if (request.has("temperature")) result.set("temperature", request.get("temperature"));
        if (request.has("top_p")) result.set("top_p", request.get("top_p"));
        if (request.has("stream")) result.set("stream", request.get("stream"));
        if (request.has("stop_sequences")) result.set("stop", request.get("stop_sequences"));

        // tools 转换: Anthropic → OpenAI
        if (request.has("tools") && request.get("tools").isArray()) {
            ArrayNode openAiTools = mapper.createArrayNode();
            for (JsonNode tool : request.get("tools")) {
                ObjectNode openAiTool = mapper.createObjectNode();
                openAiTool.put("type", "function");
                ObjectNode func = mapper.createObjectNode();
                func.put("name", tool.has("name") ? tool.get("name").asText() : "");
                if (tool.has("description")) {
                    func.put("description", tool.get("description").asText());
                }
                if (tool.has("input_schema")) {
                    func.set("parameters", tool.get("input_schema"));
                }
                openAiTool.set("function", func);
                openAiTools.add(openAiTool);
            }
            if (openAiTools.size() > 0) {
                result.set("tools", openAiTools);
            }
        }

        // tool_choice 转换
        if (request.has("tool_choice")) {
            JsonNode tc = request.get("tool_choice");
            if (tc.isObject() && tc.has("type")) {
                String tcType = tc.get("type").asText();
                switch (tcType) {
                    case "auto" -> result.put("tool_choice", "auto");
                    case "any" -> result.put("tool_choice", "required");
                    case "tool" -> {
                        ObjectNode openAiChoice = mapper.createObjectNode();
                        openAiChoice.put("type", "function");
                        ObjectNode funcChoice = mapper.createObjectNode();
                        funcChoice.put("name", tc.has("name") ? tc.get("name").asText() : "");
                        openAiChoice.set("function", funcChoice);
                        result.set("tool_choice", openAiChoice);
                    }
                }
            }
        }

        log.debug("Anthropic→Chat converted request: {}", result);
        return result;
    }

    /**
     * 转换 Anthropic assistant 消息，处理 tool_use content blocks
     */
    private void convertAnthropicAssistantMessage(JsonNode msg, ArrayNode messages) {
        ObjectNode openAiMsg = mapper.createObjectNode();
        openAiMsg.put("role", "assistant");

        JsonNode content = msg.get("content");
        if (content == null || content.isNull()) {
            openAiMsg.putNull("content");
            messages.add(openAiMsg);
            return;
        }

        if (content.isTextual()) {
            openAiMsg.put("content", content.asText());
            messages.add(openAiMsg);
            return;
        }

        // content 是数组，可能包含 text 和 tool_use blocks
        if (content.isArray()) {
            StringBuilder textContent = new StringBuilder();
            ArrayNode toolCalls = mapper.createArrayNode();
            int toolCallIndex = 0;

            for (JsonNode block : content) {
                String blockType = block.has("type") ? block.get("type").asText() : "";
                if ("text".equals(blockType) && block.has("text")) {
                    textContent.append(block.get("text").asText());
                } else if ("tool_use".equals(blockType)) {
                    ObjectNode tc = mapper.createObjectNode();
                    tc.put("id", block.has("id") ? block.get("id").asText() : UUID.randomUUID().toString());
                    tc.put("type", "function");
                    ObjectNode func = mapper.createObjectNode();
                    func.put("name", block.has("name") ? block.get("name").asText() : "");
                    if (block.has("input")) {
                        func.put("arguments", block.get("input").toString());
                    } else {
                        func.put("arguments", "{}");
                    }
                    tc.set("function", func);
                    tc.put("index", toolCallIndex++);
                    toolCalls.add(tc);
                }
            }

            if (textContent.length() > 0) {
                openAiMsg.put("content", textContent.toString());
            } else {
                openAiMsg.putNull("content");
            }

            if (toolCalls.size() > 0) {
                openAiMsg.set("tool_calls", toolCalls);
            }
        }

        messages.add(openAiMsg);
    }

    /**
     * 转换 Anthropic user 消息，处理 tool_result content blocks
     */
    private void convertAnthropicUserMessage(JsonNode msg, ArrayNode messages) {
        JsonNode content = msg.get("content");
        if (content == null || content.isNull()) {
            ObjectNode openAiMsg = mapper.createObjectNode();
            openAiMsg.put("role", "user");
            openAiMsg.put("content", "");
            messages.add(openAiMsg);
            return;
        }

        if (content.isTextual()) {
            ObjectNode openAiMsg = mapper.createObjectNode();
            openAiMsg.put("role", "user");
            openAiMsg.put("content", content.asText());
            messages.add(openAiMsg);
            return;
        }

        if (content.isArray()) {
            // 分离 tool_result 和普通内容
            List<JsonNode> toolResults = new ArrayList<>();
            StringBuilder textContent = new StringBuilder();

            for (JsonNode block : content) {
                String blockType = block.has("type") ? block.get("type").asText() : "";
                if ("tool_result".equals(blockType)) {
                    toolResults.add(block);
                } else if ("text".equals(blockType) && block.has("text")) {
                    textContent.append(block.get("text").asText());
                }
            }

            // tool_result → tool role messages
            for (JsonNode tr : toolResults) {
                ObjectNode toolMsg = mapper.createObjectNode();
                toolMsg.put("role", "tool");
                toolMsg.put("tool_call_id", tr.has("tool_use_id") ? tr.get("tool_use_id").asText() : "");
                // tool_result 的 content 可以是字符串或数组
                if (tr.has("content")) {
                    JsonNode trContent = tr.get("content");
                    if (trContent.isTextual()) {
                        toolMsg.put("content", trContent.asText());
                    } else if (trContent.isArray()) {
                        StringBuilder sb = new StringBuilder();
                        for (JsonNode part : trContent) {
                            if (part.has("text")) sb.append(part.get("text").asText());
                        }
                        toolMsg.put("content", sb.toString());
                    } else {
                        toolMsg.put("content", trContent.toString());
                    }
                } else {
                    toolMsg.put("content", "");
                }
                messages.add(toolMsg);
            }

            // 普通文本内容 → user message
            if (textContent.length() > 0) {
                ObjectNode userMsg = mapper.createObjectNode();
                userMsg.put("role", "user");
                userMsg.put("content", textContent.toString());
                messages.add(userMsg);
            }
        }
    }

    // ==================== 响应转换: OpenAI Chat → Anthropic ====================

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
        String finishReason = "end_turn";

        if (response.has("choices") && response.get("choices").isArray() && response.get("choices").size() > 0) {
            JsonNode firstChoice = response.get("choices").get(0);
            if (firstChoice.has("finish_reason") && !firstChoice.get("finish_reason").isNull()) {
                finishReason = mapFinishReason(firstChoice.get("finish_reason").asText());
            }

            if (firstChoice.has("message")) {
                JsonNode message = firstChoice.get("message");

                // 文本内容
                if (message.has("content") && !message.get("content").isNull()) {
                    ObjectNode textBlock = mapper.createObjectNode();
                    textBlock.put("type", "text");
                    textBlock.put("text", message.get("content").asText());
                    content.add(textBlock);
                }

                // tool_calls → tool_use blocks
                if (message.has("tool_calls") && message.get("tool_calls").isArray()) {
                    for (JsonNode tc : message.get("tool_calls")) {
                        ObjectNode toolUse = mapper.createObjectNode();
                        toolUse.put("type", "tool_use");
                        toolUse.put("id", tc.has("id") ? tc.get("id").asText() : UUID.randomUUID().toString());
                        if (tc.has("function")) {
                            JsonNode func = tc.get("function");
                            toolUse.put("name", func.has("name") ? func.get("name").asText() : "");
                            if (func.has("arguments")) {
                                try {
                                    JsonNode argsObj = mapper.readTree(func.get("arguments").asText());
                                    toolUse.set("input", argsObj);
                                } catch (Exception e) {
                                    toolUse.set("input", mapper.createObjectNode());
                                }
                            } else {
                                toolUse.set("input", mapper.createObjectNode());
                            }
                        }
                        content.add(toolUse);
                    }
                }
            }
        }
        result.set("content", content);
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

    // ==================== 流式转换: OpenAI Chat Stream → Anthropic Stream ====================

    private Flux<ServerSentEvent<String>> convertOpenAiChatStreamToAnthropic(Flux<ServerSentEvent<String>> upstream) {
        AtomicBoolean started = new AtomicBoolean(false);
        AtomicInteger blockIndex = new AtomicInteger(0);
        AtomicBoolean textBlockStarted = new AtomicBoolean(false);
        String msgId = "msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        // 跟踪 tool_call 状态: tcIndex → anthropic block index
        ConcurrentHashMap<Integer, Integer> toolCallBlockIndex = new ConcurrentHashMap<>();

        return upstream.flatMapIterable(event -> {
            String data = event.data();
            if (data == null || data.isBlank()) return Collections.<ServerSentEvent<String>>emptyList();
            if ("[DONE]".equals(data)) {
                return buildAnthropicStopEvents(msgId, blockIndex.get(), textBlockStarted.get(), toolCallBlockIndex);
            }

            try {
                JsonNode node = mapper.readTree(data);
                List<ServerSentEvent<String>> events = new ArrayList<>();

                if (!started.getAndSet(true)) {
                    events.addAll(buildAnthropicStartEvents(msgId, node));
                }

                if (node.has("choices") && node.get("choices").isArray()) {
                    for (JsonNode choice : node.get("choices")) {
                        if (!choice.has("delta")) continue;
                        JsonNode delta = choice.get("delta");

                        // 文本内容
                        if (delta.has("content") && !delta.get("content").isNull()) {
                            if (!textBlockStarted.getAndSet(true)) {
                                // 发送 content_block_start (text)
                                ObjectNode blockStart = mapper.createObjectNode();
                                blockStart.put("type", "content_block_start");
                                blockStart.put("index", blockIndex.get());
                                ObjectNode contentBlock = mapper.createObjectNode();
                                contentBlock.put("type", "text");
                                contentBlock.put("text", "");
                                blockStart.set("content_block", contentBlock);
                                events.add(sse("content_block_start", blockStart.toString()));
                            }

                            ObjectNode deltaEvent = mapper.createObjectNode();
                            deltaEvent.put("type", "content_block_delta");
                            deltaEvent.put("index", blockIndex.get());
                            ObjectNode deltaObj = mapper.createObjectNode();
                            deltaObj.put("type", "text_delta");
                            deltaObj.put("text", delta.get("content").asText());
                            deltaEvent.set("delta", deltaObj);
                            events.add(sse("content_block_delta", deltaEvent.toString()));
                        }

                        // tool_calls
                        if (delta.has("tool_calls") && delta.get("tool_calls").isArray()) {
                            for (JsonNode tc : delta.get("tool_calls")) {
                                int tcIndex = tc.has("index") ? tc.get("index").asInt() : 0;

                                if (!toolCallBlockIndex.containsKey(tcIndex)) {
                                    // 关闭之前的 text block (如果有)
                                    if (textBlockStarted.get() && blockIndex.get() == 0 && toolCallBlockIndex.isEmpty()) {
                                        ObjectNode blockStop = mapper.createObjectNode();
                                        blockStop.put("type", "content_block_stop");
                                        blockStop.put("index", blockIndex.get());
                                        events.add(sse("content_block_stop", blockStop.toString()));
                                        blockIndex.incrementAndGet();
                                    }

                                    int currentBlockIdx = blockIndex.getAndIncrement();
                                    toolCallBlockIndex.put(tcIndex, currentBlockIdx);

                                    // content_block_start (tool_use)
                                    String tcId = tc.has("id") ? tc.get("id").asText() : "toolu_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
                                    String tcName = "";
                                    if (tc.has("function") && tc.get("function").has("name")) {
                                        tcName = tc.get("function").get("name").asText();
                                    }

                                    ObjectNode blockStart = mapper.createObjectNode();
                                    blockStart.put("type", "content_block_start");
                                    blockStart.put("index", currentBlockIdx);
                                    ObjectNode contentBlock = mapper.createObjectNode();
                                    contentBlock.put("type", "tool_use");
                                    contentBlock.put("id", tcId);
                                    contentBlock.put("name", tcName);
                                    contentBlock.set("input", mapper.createObjectNode());
                                    blockStart.set("content_block", contentBlock);
                                    events.add(sse("content_block_start", blockStart.toString()));
                                }

                                // arguments delta → input_json_delta
                                if (tc.has("function") && tc.get("function").has("arguments")) {
                                    String argDelta = tc.get("function").get("arguments").asText();
                                    if (argDelta != null && !argDelta.isEmpty()) {
                                        int currentBlockIdx = toolCallBlockIndex.get(tcIndex);
                                        ObjectNode deltaEvent = mapper.createObjectNode();
                                        deltaEvent.put("type", "content_block_delta");
                                        deltaEvent.put("index", currentBlockIdx);
                                        ObjectNode deltaObj = mapper.createObjectNode();
                                        deltaObj.put("type", "input_json_delta");
                                        deltaObj.put("partial_json", argDelta);
                                        deltaEvent.set("delta", deltaObj);
                                        events.add(sse("content_block_delta", deltaEvent.toString()));
                                    }
                                }
                            }
                        }

                        // finish_reason 在 message_delta 中处理
                        if (choice.has("finish_reason") && !choice.get("finish_reason").isNull()) {
                            // 不在这里处理，等 [DONE] 时统一发送
                        }
                    }
                }
                return events;
            } catch (Exception e) {
                log.debug("Failed to parse OpenAI stream event: {}", data);
                return Collections.<ServerSentEvent<String>>emptyList();
            }
        });
    }

    private List<ServerSentEvent<String>> buildAnthropicStartEvents(String msgId, JsonNode firstChunk) {
        List<ServerSentEvent<String>> events = new ArrayList<>();

        ObjectNode messageStart = mapper.createObjectNode();
        messageStart.put("type", "message_start");
        ObjectNode message = mapper.createObjectNode();
        message.put("id", msgId);
        message.put("type", "message");
        message.put("role", "assistant");
        if (firstChunk.has("model")) message.put("model", firstChunk.get("model").asText());
        message.set("content", mapper.createArrayNode());
        message.putNull("stop_reason");
        ObjectNode usage = mapper.createObjectNode();
        usage.put("input_tokens", 0);
        usage.put("output_tokens", 0);
        message.set("usage", usage);
        messageStart.set("message", message);
        events.add(sse("message_start", messageStart.toString()));

        return events;
    }

    private List<ServerSentEvent<String>> buildAnthropicStopEvents(
            String msgId, int blockIndex, boolean hasTextBlock,
            ConcurrentHashMap<Integer, Integer> toolCallBlockIndex) {
        List<ServerSentEvent<String>> events = new ArrayList<>();

        // 关闭最后一个 content_block
        if (hasTextBlock || !toolCallBlockIndex.isEmpty()) {
            // 如果有 text block 且没有 tool_calls，关闭 text block
            if (hasTextBlock && toolCallBlockIndex.isEmpty()) {
                ObjectNode blockStop = mapper.createObjectNode();
                blockStop.put("type", "content_block_stop");
                blockStop.put("index", 0);
                events.add(sse("content_block_stop", blockStop.toString()));
            }
            // 关闭所有 tool_call blocks
            if (!toolCallBlockIndex.isEmpty()) {
                for (int anthropicIdx : toolCallBlockIndex.values()) {
                    ObjectNode blockStop = mapper.createObjectNode();
                    blockStop.put("type", "content_block_stop");
                    blockStop.put("index", anthropicIdx);
                    events.add(sse("content_block_stop", blockStop.toString()));
                }
            }
        }

        // message_delta
        ObjectNode messageDelta = mapper.createObjectNode();
        messageDelta.put("type", "message_delta");
        ObjectNode delta = mapper.createObjectNode();
        delta.put("stop_reason", toolCallBlockIndex.isEmpty() ? "end_turn" : "tool_use");
        delta.putNull("stop_sequence");
        messageDelta.set("delta", delta);
        ObjectNode usage = mapper.createObjectNode();
        usage.put("output_tokens", 0);
        messageDelta.set("usage", usage);
        events.add(sse("message_delta", messageDelta.toString()));

        // message_stop
        ObjectNode messageStop = mapper.createObjectNode();
        messageStop.put("type", "message_stop");
        events.add(sse("message_stop", messageStop.toString()));

        return events;
    }

    private ServerSentEvent<String> sse(String eventType, String data) {
        return ServerSentEvent.<String>builder().event(eventType).data(data).build();
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

    private String mapFinishReason(String openAiReason) {
        if (openAiReason == null) return "end_turn";
        return switch (openAiReason) {
            case "stop" -> "end_turn";
            case "length" -> "max_tokens";
            case "content_filter" -> "end_turn";
            case "tool_calls" -> "tool_use";
            default -> "end_turn";
        };
    }
}
