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
 * OpenAI Chat Completions → Anthropic Messages 协议转换器
 *
 * 请求方向: 客户端发 OpenAI Chat 格式，转为 Anthropic Messages 格式发给上游
 * 响应方向: 上游返回 Anthropic 格式，转为 OpenAI Chat 格式返回给客户端
 */
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

    // ==================== 请求转换: OpenAI Chat → Anthropic ====================

    @Override
    public ObjectNode convertRequest(ObjectNode request) {
        ObjectNode result = mapper.createObjectNode();

        if (request.has("model")) result.set("model", request.get("model"));

        // 提取 system messages
        StringBuilder systemText = new StringBuilder();
        ArrayNode anthropicMessages = mapper.createArrayNode();

        if (request.has("messages")) {
            for (JsonNode msg : request.get("messages")) {
                String role = msg.has("role") ? msg.get("role").asText() : "";
                switch (role) {
                    case "system", "developer" -> {
                        if (systemText.length() > 0) systemText.append("\n");
                        systemText.append(getContentText(msg));
                    }
                    case "assistant" -> convertAssistantMessage(msg, anthropicMessages);
                    case "tool" -> convertToolMessage(msg, anthropicMessages);
                    default -> convertUserMessage(msg, anthropicMessages);
                }
            }
        }

        if (systemText.length() > 0) {
            result.put("system", systemText.toString());
        }
        // 确保消息交替: Anthropic 要求 user/assistant 交替
        result.set("messages", ensureAlternatingRoles(anthropicMessages));

        // max_tokens (Anthropic 必需)
        if (request.has("max_tokens")) {
            result.set("max_tokens", request.get("max_tokens"));
        } else if (request.has("max_completion_tokens")) {
            result.set("max_tokens", request.get("max_completion_tokens"));
        } else {
            result.put("max_tokens", 8192);
        }

        if (request.has("temperature")) result.set("temperature", request.get("temperature"));
        if (request.has("top_p")) result.set("top_p", request.get("top_p"));
        if (request.has("stream")) result.set("stream", request.get("stream"));
        if (request.has("stop")) result.set("stop_sequences", request.get("stop"));

        // tools 转换: OpenAI → Anthropic
        if (request.has("tools") && request.get("tools").isArray()) {
            ArrayNode anthropicTools = mapper.createArrayNode();
            for (JsonNode tool : request.get("tools")) {
                if (tool.has("type") && "function".equals(tool.get("type").asText()) && tool.has("function")) {
                    JsonNode func = tool.get("function");
                    ObjectNode anthropicTool = mapper.createObjectNode();
                    anthropicTool.put("name", func.has("name") ? func.get("name").asText() : "");
                    if (func.has("description")) {
                        anthropicTool.put("description", func.get("description").asText());
                    }
                    if (func.has("parameters")) {
                        anthropicTool.set("input_schema", func.get("parameters"));
                    } else {
                        // Anthropic 要求 input_schema 存在
                        ObjectNode emptySchema = mapper.createObjectNode();
                        emptySchema.put("type", "object");
                        emptySchema.set("properties", mapper.createObjectNode());
                        anthropicTool.set("input_schema", emptySchema);
                    }
                    anthropicTools.add(anthropicTool);
                }
            }
            if (anthropicTools.size() > 0) {
                result.set("tools", anthropicTools);
            }
        }

        // tool_choice 转换
        if (request.has("tool_choice")) {
            JsonNode tc = request.get("tool_choice");
            if (tc.isTextual()) {
                String choice = tc.asText();
                ObjectNode anthropicChoice = mapper.createObjectNode();
                switch (choice) {
                    case "auto" -> anthropicChoice.put("type", "auto");
                    case "none" -> { /* Anthropic 没有 none，不传 tool_choice */ }
                    case "required" -> anthropicChoice.put("type", "any");
                    default -> anthropicChoice.put("type", "auto");
                }
                if (anthropicChoice.has("type")) {
                    result.set("tool_choice", anthropicChoice);
                }
            } else if (tc.isObject() && tc.has("function")) {
                ObjectNode anthropicChoice = mapper.createObjectNode();
                anthropicChoice.put("type", "tool");
                anthropicChoice.put("name", tc.get("function").has("name") ? tc.get("function").get("name").asText() : "");
                result.set("tool_choice", anthropicChoice);
            }
        }

        log.debug("Chat→Anthropic converted request: {}", result);
        return result;
    }

    /**
     * 转换 assistant 消息，处理 tool_calls
     */
    private void convertAssistantMessage(JsonNode msg, ArrayNode messages) {
        ObjectNode anthropicMsg = mapper.createObjectNode();
        anthropicMsg.put("role", "assistant");
        ArrayNode content = mapper.createArrayNode();

        // 文本内容
        String text = getContentText(msg);
        if (!text.isEmpty()) {
            ObjectNode textBlock = mapper.createObjectNode();
            textBlock.put("type", "text");
            textBlock.put("text", text);
            content.add(textBlock);
        }

        // tool_calls → tool_use content blocks
        if (msg.has("tool_calls") && msg.get("tool_calls").isArray()) {
            for (JsonNode tc : msg.get("tool_calls")) {
                ObjectNode toolUse = mapper.createObjectNode();
                toolUse.put("type", "tool_use");
                toolUse.put("id", tc.has("id") ? tc.get("id").asText() : UUID.randomUUID().toString());
                if (tc.has("function")) {
                    JsonNode func = tc.get("function");
                    toolUse.put("name", func.has("name") ? func.get("name").asText() : "");
                    // arguments 是 JSON 字符串，需要解析为对象
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

        if (content.size() > 0) {
            anthropicMsg.set("content", content);
        } else {
            // Anthropic 不允许空 content，给个空文本
            ArrayNode emptyContent = mapper.createArrayNode();
            ObjectNode emptyText = mapper.createObjectNode();
            emptyText.put("type", "text");
            emptyText.put("text", "");
            emptyContent.add(emptyText);
            anthropicMsg.set("content", emptyContent);
        }
        messages.add(anthropicMsg);
    }

    /**
     * 转换 tool role 消息 → Anthropic tool_result content block (放在 user 消息中)
     */
    private void convertToolMessage(JsonNode msg, ArrayNode messages) {
        ObjectNode toolResult = mapper.createObjectNode();
        toolResult.put("type", "tool_result");
        toolResult.put("tool_use_id", msg.has("tool_call_id") ? msg.get("tool_call_id").asText() : "");

        String content = getContentText(msg);
        toolResult.put("content", content);

        // Anthropic 要求 tool_result 在 user 消息中
        // 检查最后一条消息是否是 user，如果是则追加到其 content 中
        if (messages.size() > 0) {
            JsonNode lastMsg = messages.get(messages.size() - 1);
            if ("user".equals(lastMsg.has("role") ? lastMsg.get("role").asText() : "")) {
                // 追加到现有 user 消息的 content 数组
                if (lastMsg.has("content") && lastMsg.get("content").isArray()) {
                    ((ArrayNode) lastMsg.get("content")).add(toolResult);
                } else {
                    // 将现有文本内容转为数组格式
                    ArrayNode contentArr = mapper.createArrayNode();
                    if (lastMsg.has("content") && !lastMsg.get("content").isNull()) {
                        ObjectNode textBlock = mapper.createObjectNode();
                        textBlock.put("type", "text");
                        textBlock.put("text", lastMsg.get("content").asText());
                        contentArr.add(textBlock);
                    }
                    contentArr.add(toolResult);
                    ((ObjectNode) lastMsg).set("content", contentArr);
                }
                return;
            }
        }

        // 创建新的 user 消息包含 tool_result
        ObjectNode userMsg = mapper.createObjectNode();
        userMsg.put("role", "user");
        ArrayNode contentArr = mapper.createArrayNode();
        contentArr.add(toolResult);
        userMsg.set("content", contentArr);
        messages.add(userMsg);
    }

    private void convertUserMessage(JsonNode msg, ArrayNode messages) {
        ObjectNode anthropicMsg = mapper.createObjectNode();
        anthropicMsg.put("role", "user");
        if (msg.has("content")) {
            JsonNode content = msg.get("content");
            if (content.isTextual()) {
                anthropicMsg.put("content", content.asText());
            } else if (content.isArray()) {
                anthropicMsg.set("content", content);
            } else {
                anthropicMsg.put("content", content.asText());
            }
        }
        messages.add(anthropicMsg);
    }

    /**
     * 确保消息交替 user/assistant，合并相邻同角色消息
     */
    private ArrayNode ensureAlternatingRoles(ArrayNode messages) {
        if (messages.size() <= 1) return messages;

        ArrayNode result = mapper.createArrayNode();
        for (int i = 0; i < messages.size(); i++) {
            JsonNode current = messages.get(i);
            String currentRole = current.has("role") ? current.get("role").asText() : "user";

            if (result.size() > 0) {
                JsonNode last = result.get(result.size() - 1);
                String lastRole = last.has("role") ? last.get("role").asText() : "";
                if (currentRole.equals(lastRole)) {
                    // 合并相邻同角色消息
                    mergeMessages((ObjectNode) last, current);
                    continue;
                }
            }
            result.add(current);
        }
        return result;
    }

    private void mergeMessages(ObjectNode target, JsonNode source) {
        // 将 source 的 content 追加到 target
        ArrayNode targetContent = ensureContentArray(target);
        JsonNode sourceContent = source.get("content");
        if (sourceContent != null) {
            if (sourceContent.isArray()) {
                for (JsonNode item : sourceContent) {
                    targetContent.add(item);
                }
            } else if (sourceContent.isTextual()) {
                ObjectNode textBlock = mapper.createObjectNode();
                textBlock.put("type", "text");
                textBlock.put("text", sourceContent.asText());
                targetContent.add(textBlock);
            }
        }
        target.set("content", targetContent);
    }

    private ArrayNode ensureContentArray(ObjectNode msg) {
        if (msg.has("content") && msg.get("content").isArray()) {
            return (ArrayNode) msg.get("content");
        }
        ArrayNode arr = mapper.createArrayNode();
        if (msg.has("content") && !msg.get("content").isNull()) {
            ObjectNode textBlock = mapper.createObjectNode();
            textBlock.put("type", "text");
            textBlock.put("text", msg.get("content").asText());
            arr.add(textBlock);
        }
        msg.set("content", arr);
        return arr;
    }

    // ==================== 响应转换: Anthropic → OpenAI Chat ====================

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

        StringBuilder textContent = new StringBuilder();
        ArrayNode toolCalls = mapper.createArrayNode();
        int toolCallIndex = 0;

        if (response.has("content") && response.get("content").isArray()) {
            for (JsonNode block : response.get("content")) {
                String blockType = block.has("type") ? block.get("type").asText() : "";
                if ("text".equals(blockType) && block.has("text")) {
                    textContent.append(block.get("text").asText());
                } else if ("tool_use".equals(blockType)) {
                    ObjectNode tc = mapper.createObjectNode();
                    tc.put("id", block.has("id") ? block.get("id").asText() : "");
                    tc.put("type", "function");
                    ObjectNode func = mapper.createObjectNode();
                    func.put("name", block.has("name") ? block.get("name").asText() : "");
                    // input 是对象，需要序列化为字符串
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

        // finish_reason 映射
        String stopReason = response.has("stop_reason") ? response.get("stop_reason").asText() : "end_turn";
        choice.put("finish_reason", mapStopReason(stopReason));
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
                    (usage.has("completion_tokens") ? usage.get("completion_tokens").asInt() : 0));
            result.set("usage", openAiUsage);
        }

        return result;
    }

    // ==================== 流式转换: Anthropic Stream → OpenAI Chat Stream ====================

    private Flux<ServerSentEvent<String>> convertAnthropicStreamToOpenAiChat(Flux<ServerSentEvent<String>> upstream) {
        String chatId = "chatcmpl-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        AtomicBoolean sentRole = new AtomicBoolean(false);
        AtomicInteger toolCallIndex = new AtomicInteger(-1);
        // 跟踪当前 content_block 类型: text 或 tool_use
        ConcurrentHashMap<Integer, String> blockTypes = new ConcurrentHashMap<>();
        ConcurrentHashMap<Integer, String> toolCallIds = new ConcurrentHashMap<>();
        ConcurrentHashMap<Integer, String> toolCallNames = new ConcurrentHashMap<>();

        return upstream.flatMapIterable(event -> {
            String data = event.data();
            if (data == null || data.isBlank()) return Collections.<ServerSentEvent<String>>emptyList();

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
                        ObjectNode chunk = buildStreamChunk(chatId, model, "assistant", null, null, null);
                        yield List.of(ServerSentEvent.<String>builder().data(chunk.toString()).build());
                    }
                    case "content_block_start" -> {
                        int index = node.has("index") ? node.get("index").asInt() : 0;
                        JsonNode contentBlock = node.get("content_block");
                        String blockType = contentBlock != null && contentBlock.has("type") ? contentBlock.get("type").asText() : "text";
                        blockTypes.put(index, blockType);

                        if ("tool_use".equals(blockType)) {
                            int tcIdx = toolCallIndex.incrementAndGet();
                            String tcId = contentBlock.has("id") ? contentBlock.get("id").asText() : "";
                            String tcName = contentBlock.has("name") ? contentBlock.get("name").asText() : "";
                            toolCallIds.put(index, tcId);
                            toolCallNames.put(index, tcName);

                            // 发送 tool_calls 开始 chunk
                            ObjectNode chunk = buildToolCallStartChunk(chatId, tcIdx, tcId, tcName);
                            yield List.of(ServerSentEvent.<String>builder().data(chunk.toString()).build());
                        }
                        yield Collections.<ServerSentEvent<String>>emptyList();
                    }
                    case "content_block_delta" -> {
                        int index = node.has("index") ? node.get("index").asInt() : 0;
                        String blockType = blockTypes.getOrDefault(index, "text");
                        JsonNode delta = node.get("delta");

                        if ("text".equals(blockType) || "text_delta".equals(delta != null ? delta.has("type") ? delta.get("type").asText() : "" : "")) {
                            String text = delta != null && delta.has("text") ? delta.get("text").asText() : "";
                            ObjectNode chunk = buildStreamChunk(chatId, null, null, text, null, null);
                            yield List.of(ServerSentEvent.<String>builder().data(chunk.toString()).build());
                        } else if ("tool_use".equals(blockType) || "input_json_delta".equals(delta != null ? delta.has("type") ? delta.get("type").asText() : "" : "")) {
                            String partialJson = delta != null && delta.has("partial_json") ? delta.get("partial_json").asText() : "";
                            int tcIdx = toolCallIndex.get();
                            ObjectNode chunk = buildToolCallDeltaChunk(chatId, tcIdx, partialJson);
                            yield List.of(ServerSentEvent.<String>builder().data(chunk.toString()).build());
                        }
                        yield Collections.<ServerSentEvent<String>>emptyList();
                    }
                    case "content_block_stop" -> Collections.<ServerSentEvent<String>>emptyList();
                    case "message_delta" -> {
                        String stopReason = null;
                        if (node.has("delta") && node.get("delta").has("stop_reason")) {
                            stopReason = mapStopReason(node.get("delta").get("stop_reason").asText());
                        }
                        ObjectNode chunk = buildStreamChunk(chatId, null, null, null, stopReason, null);
                        List<ServerSentEvent<String>> events = new ArrayList<>();
                        events.add(ServerSentEvent.<String>builder().data(chunk.toString()).build());
                        if (node.has("usage")) {
                            ObjectNode usageChunk = buildUsageChunk(chatId, node.get("usage"));
                            events.add(ServerSentEvent.<String>builder().data(usageChunk.toString()).build());
                        }
                        yield events;
                    }
                    case "message_stop" -> List.of(
                            ServerSentEvent.<String>builder().data("[DONE]").build()
                    );
                    default -> Collections.<ServerSentEvent<String>>emptyList();
                };
            } catch (Exception e) {
                log.debug("Failed to parse Anthropic stream event: {}", data);
                return Collections.<ServerSentEvent<String>>emptyList();
            }
        });
    }

    private ObjectNode buildStreamChunk(String id, String model, String role, String content, String finishReason, ArrayNode toolCalls) {
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

    private String mapStopReason(String anthropicReason) {
        if (anthropicReason == null) return "stop";
        return switch (anthropicReason) {
            case "end_turn" -> "stop";
            case "max_tokens" -> "length";
            case "stop_sequence" -> "stop";
            case "tool_use" -> "tool_calls";
            default -> "stop";
        };
    }
}
