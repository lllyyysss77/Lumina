package com.lumina.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Component
public class ResponsesToOpenAiChatConverter implements ProtocolConverter {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String DEEPSEEK_REASONING_PREFIX = "deepseek-reasoning:";

    @Override
    public ProtocolType sourceType() {
        return ProtocolType.OPENAI_RESPONSES;
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

        // instructions → system message
        if (request.has("instructions") && !request.get("instructions").isNull()) {
            ObjectNode systemMsg = mapper.createObjectNode();
            systemMsg.put("role", "system");
            systemMsg.put("content", request.get("instructions").asText());
            messages.add(systemMsg);
        }

        if (request.has("input")) {
            // 标准 Responses API 格式: input 字段
            JsonNode input = request.get("input");
            if (input.isArray()) {
                String pendingReasoningContent = null;
                ArrayNode pendingToolCalls = mapper.createArrayNode();
                for (JsonNode item : input) {
                    if (item.isTextual()) {
                        if (pendingToolCalls.size() > 0) {
                            messages.add(buildAssistantToolCallsMessage(pendingToolCalls, pendingReasoningContent));
                            pendingToolCalls = mapper.createArrayNode();
                        }
                        pendingReasoningContent = null;
                        ObjectNode msg = mapper.createObjectNode();
                        msg.put("role", "user");
                        msg.put("content", item.asText());
                        messages.add(msg);
                    } else if (item.isObject()) {
                        if (isReasoningItem(item)) {
                            pendingReasoningContent = mergeReasoningContent(
                                    pendingReasoningContent,
                                    extractReasoningContent(item)
                            );
                            continue;
                        }
                        if (isFunctionCallItem(item)) {
                            pendingToolCalls.add(convertFunctionCallToToolCall(item));
                            continue;
                        }
                        if (pendingToolCalls.size() > 0) {
                            messages.add(buildAssistantToolCallsMessage(pendingToolCalls, pendingReasoningContent));
                            pendingToolCalls = mapper.createArrayNode();
                        }
                        ObjectNode converted = convertInputItem(item);
                        if (converted != null) {
                            attachReasoningContent(converted, pendingReasoningContent);
                            messages.add(converted);
                        }
                        pendingReasoningContent = null;
                    }
                }
                if (pendingToolCalls.size() > 0) {
                    messages.add(buildAssistantToolCallsMessage(pendingToolCalls, pendingReasoningContent));
                }
            } else if (input.isTextual()) {
                ObjectNode msg = mapper.createObjectNode();
                msg.put("role", "user");
                msg.put("content", input.asText());
                messages.add(msg);
            }
        } else if (request.has("messages")) {
            // 兼容: 有些客户端发到 /v1/responses 但用的是 messages 格式
            for (JsonNode item : request.get("messages")) {
                if (item.isObject()) {
                    messages.add(normalizeMessage(item));
                }
            }
        }

        result.set("messages", messages);

        if (request.has("max_output_tokens")) {
            result.set("max_tokens", request.get("max_output_tokens"));
        } else if (request.has("max_tokens")) {
            result.set("max_tokens", request.get("max_tokens"));
        }
        if (request.has("temperature")) result.set("temperature", request.get("temperature"));
        if (request.has("top_p")) result.set("top_p", request.get("top_p"));
        if (request.has("stream")) result.set("stream", request.get("stream"));

        // 转换 tools 定义
        // Responses API 格式: {type:"function", name:"x", description:"", parameters:{}}
        // Chat Completions 格式: {type:"function", function:{name:"x", description:"", parameters:{}}}
        if (request.has("tools") && request.get("tools").isArray()) {
            ArrayNode chatTools = mapper.createArrayNode();
            for (JsonNode tool : request.get("tools")) {
                if (!tool.has("type")) {
                    chatTools.add(tool);
                    continue;
                }
                String toolType = tool.get("type").asText();
                if ("function".equals(toolType)) {
                    if (tool.has("function")) {
                        // 已经是 Chat 嵌套格式，直接透传
                        chatTools.add(tool);
                    } else if (tool.has("name")) {
                        // Responses API 扁平格式 → Chat 嵌套格式
                        ObjectNode chatTool = mapper.createObjectNode();
                        chatTool.put("type", "function");
                        ObjectNode func = mapper.createObjectNode();
                        if (tool.has("name")) func.put("name", tool.get("name").asText());
                        if (tool.has("description")) func.put("description", tool.get("description").asText());
                        if (tool.has("parameters")) func.set("parameters", tool.get("parameters"));
                        chatTool.set("function", func);
                        chatTools.add(chatTool);
                    } else {
                        chatTools.add(tool);
                    }
                }
                // 跳过非 function 类型 (如 image_generation, web_search 等上游不支持的)
            }
            if (chatTools.size() > 0) {
                result.set("tools", chatTools);
            }
        }
        if (request.has("tool_choice")) {
            result.set("tool_choice", convertToolChoice(request.get("tool_choice")));
        }

        log.debug("Responses→Chat converted request: {}", result);
        return result;
    }

    private ObjectNode normalizeMessage(JsonNode item) {
        String role = item.has("role") ? item.get("role").asText() : "user";
        if ("developer".equals(role)) {
            role = "system";
        }
        ObjectNode msg = mapper.createObjectNode();
        msg.put("role", role);
        if (item.has("content")) {
            JsonNode content = item.get("content");
            if (content.isTextual()) {
                msg.put("content", content.asText());
            } else if (content.isArray()) {
                JsonNode convertedContent = convertResponsesContentToChatContent(content);
                msg.set("content", convertedContent);
            } else {
                msg.set("content", content);
            }
        } else if ("assistant".equals(role)) {
            msg.putNull("content");
        }
        if (item.has("tool_calls")) msg.set("tool_calls", item.get("tool_calls"));
        if (item.has("tool_call_id")) msg.set("tool_call_id", item.get("tool_call_id"));
        if (item.has("reasoning_content")) msg.set("reasoning_content", item.get("reasoning_content"));
        return msg;
    }

    /**
     * 将 Responses API 的 tool_choice 转换为 Chat Completions 格式。
     * Responses: {"type":"function","name":"x"}
     * Chat:      {"type":"function","function":{"name":"x"}}
     */
    private JsonNode convertToolChoice(JsonNode toolChoice) {
        if (toolChoice.isTextual()) {
            String val = toolChoice.asText();
            // "required" 不是标准 Chat API 值，DeepSeek 等供应商不支持，降级为 auto
            if ("required".equals(val)) return mapper.getNodeFactory().textNode("auto");
            return toolChoice;
        }
        if (toolChoice.isObject()) {
            if (toolChoice.has("function")) return toolChoice; // 已是 Chat 嵌套格式
            if (toolChoice.has("name") && "function".equals(
                    toolChoice.has("type") ? toolChoice.get("type").asText() : "")) {
                // Responses 扁平格式 → Chat 嵌套格式
                ObjectNode chatToolChoice = mapper.createObjectNode();
                chatToolChoice.put("type", "function");
                ObjectNode func = mapper.createObjectNode();
                func.put("name", toolChoice.get("name").asText());
                chatToolChoice.set("function", func);
                return chatToolChoice;
            }
        }
        return toolChoice;
    }

    private ObjectNode convertInputItem(JsonNode item) {
        String type = item.has("type") ? item.get("type").asText() : "";

        switch (type) {
            case "reasoning" -> {
                return null;
            }
            case "function_call" -> {
                // function_call → assistant message with tool_calls
                ObjectNode msg = mapper.createObjectNode();
                msg.put("role", "assistant");
                msg.putNull("content");
                ArrayNode toolCalls = mapper.createArrayNode();
                toolCalls.add(convertFunctionCallToToolCall(item));
                msg.set("tool_calls", toolCalls);
                return msg;
            }
            case "function_call_output" -> {
                // function_call_output → tool message
                ObjectNode msg = mapper.createObjectNode();
                msg.put("role", "tool");
                msg.put("tool_call_id", item.has("call_id") ? item.get("call_id").asText() : "");
                String output = "";
                if (item.has("output")) {
                    JsonNode outputNode = item.get("output");
                    if (outputNode.isTextual()) {
                        output = outputNode.asText();
                    } else {
                        output = outputNode.toString();
                    }
                }
                msg.put("content", output);
                return msg;
            }
            default -> {
                // message, input_text 等 → 普通消息
                return normalizeMessage(item);
            }
        }
    }

    @Override
    public ObjectNode convertResponse(ObjectNode response) {
        ObjectNode result = mapper.createObjectNode();
        String id = response.has("id") ? response.get("id").asText() : "resp_" + UUID.randomUUID();
        result.put("id", id);
        result.put("object", "response");
        if (response.has("model")) result.set("model", response.get("model"));
        result.put("status", "completed");

        ArrayNode output = mapper.createArrayNode();
        if (response.has("choices") && response.get("choices").isArray()) {
            for (JsonNode choice : response.get("choices")) {
                if (choice.has("message")) {
                    JsonNode message = choice.get("message");
                    String reasoningContent = textValue(message, "reasoning_content");
                    if (!reasoningContent.isBlank()) {
                        output.add(buildReasoningOutputItem(reasoningContent, true));
                    }

                    // 文本消息
                    if (message.has("content") && !message.get("content").isNull()) {
                        ObjectNode outputItem = mapper.createObjectNode();
                        outputItem.put("type", "message");
                        outputItem.put("role", "assistant");
                        ArrayNode content = mapper.createArrayNode();
                        ObjectNode textPart = mapper.createObjectNode();
                        textPart.put("type", "output_text");
                        textPart.put("text", message.get("content").asText());
                        content.add(textPart);
                        outputItem.set("content", content);
                        output.add(outputItem);
                    }

                    // tool_calls → function_call items
                    if (message.has("tool_calls") && message.get("tool_calls").isArray()) {
                        for (JsonNode tc : message.get("tool_calls")) {
                            ObjectNode fcItem = mapper.createObjectNode();
                            fcItem.put("type", "function_call");
                            fcItem.put("status", "completed");
                            fcItem.put("call_id", tc.has("id") ? tc.get("id").asText() : "");
                            if (tc.has("function")) {
                                fcItem.put("name", tc.get("function").has("name") ? tc.get("function").get("name").asText() : "");
                                fcItem.put("arguments", tc.get("function").has("arguments") ? tc.get("function").get("arguments").asText() : "");
                            }
                            output.add(fcItem);
                        }
                    }
                }
            }
        }
        result.set("output", output);

        if (response.has("usage")) {
            JsonNode usage = response.get("usage");
            ObjectNode respUsage = mapper.createObjectNode();
            respUsage.put("input_tokens", usage.has("prompt_tokens") ? usage.get("prompt_tokens").asInt() : 0);
            respUsage.put("output_tokens", usage.has("completion_tokens") ? usage.get("completion_tokens").asInt() : 0);
            respUsage.put("total_tokens",
                    (usage.has("prompt_tokens") ? usage.get("prompt_tokens").asInt() : 0) +
                    (usage.has("completion_tokens") ? usage.get("completion_tokens").asInt() : 0));
            result.set("usage", respUsage);
        }

        return result;
    }

    @Override
    public Flux<ServerSentEvent<String>> convertStreamResponse(Flux<ServerSentEvent<String>> upstream) {
        String responseId = "resp_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        java.util.concurrent.atomic.AtomicBoolean started = new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicInteger outputIndex = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger textOutputIndex = new java.util.concurrent.atomic.AtomicInteger(-1);
        StringBuilder fullText = new StringBuilder();
        // 跟踪每个 tool_call 的状态: index → {id, name, arguments}
        java.util.concurrent.ConcurrentHashMap<Integer, ToolCallState> toolCallStates = new java.util.concurrent.ConcurrentHashMap<>();
        ReasoningState reasoningState = new ReasoningState();
        java.util.concurrent.atomic.AtomicBoolean hasTextOutput = new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicBoolean textItemStarted = new java.util.concurrent.atomic.AtomicBoolean(false);

        return upstream.flatMapIterable(event -> {
            String data = event.data();
            if (data == null || data.isBlank()) return java.util.Collections.<ServerSentEvent<String>>emptyList();

            if ("[DONE]".equals(data)) {
                return buildDoneEvents(
                        responseId,
                        fullText,
                        hasTextOutput.get(),
                        textOutputIndex.get(),
                        toolCallStates,
                        reasoningState
                );
            }

            try {
                JsonNode node = mapper.readTree(data);
                java.util.List<ServerSentEvent<String>> events = new java.util.ArrayList<>();

                if (!started.getAndSet(true)) {
                    events.addAll(buildStartEvents(responseId));
                }

                if (node.has("choices") && node.get("choices").isArray()) {
                    for (JsonNode choice : node.get("choices")) {
                        if (!choice.has("delta")) continue;
                        JsonNode delta = choice.get("delta");

                        // DeepSeek thinking mode streams reasoning_content before content.
                        if (delta.has("reasoning_content") && !delta.get("reasoning_content").isNull()) {
                            String reasoningDelta = delta.get("reasoning_content").asText();
                            if (!reasoningDelta.isEmpty()) {
                                if (!reasoningState.started) {
                                    reasoningState.started = true;
                                    reasoningState.outputIndex = outputIndex.getAndIncrement();
                                    reasoningState.itemId = "rs_" + responseId.substring(Math.max(0, responseId.length() - 12));

                                    ObjectNode itemAdded = mapper.createObjectNode();
                                    itemAdded.put("type", "response.output_item.added");
                                    itemAdded.put("output_index", reasoningState.outputIndex);
                                    itemAdded.set("item", buildReasoningOutputItem("", false));
                                    ((ObjectNode) itemAdded.get("item")).put("id", reasoningState.itemId);
                                    events.add(sse("response.output_item.added", itemAdded.toString()));
                                }
                                reasoningState.content.append(reasoningDelta);
                            }
                        }

                        // 处理文本内容
                        if (delta.has("content") && !delta.get("content").isNull()) {
                            String text = delta.get("content").asText();
                            if (!text.isEmpty() && !textItemStarted.getAndSet(true)) {
                                hasTextOutput.set(true);
                                textOutputIndex.set(outputIndex.getAndIncrement());
                                events.addAll(buildTextItemStart(textOutputIndex.get()));
                            }
                            if (!text.isEmpty()) {
                                fullText.append(text);

                                ObjectNode deltaEvent = mapper.createObjectNode();
                                deltaEvent.put("type", "response.output_text.delta");
                                deltaEvent.put("item_id", "msg_0");
                                deltaEvent.put("output_index", textOutputIndex.get());
                                deltaEvent.put("content_index", 0);
                                deltaEvent.put("delta", text);
                                events.add(sse("response.output_text.delta", deltaEvent.toString()));
                            }
                        }

                        // 处理 tool_calls
                        if (delta.has("tool_calls") && delta.get("tool_calls").isArray()) {
                            reasoningState.hasToolCall = true;
                            for (JsonNode tc : delta.get("tool_calls")) {
                                int tcIndex = tc.has("index") ? tc.get("index").asInt() : 0;
                                ToolCallState state = toolCallStates.computeIfAbsent(tcIndex, k -> new ToolCallState());

                                // 第一次出现该 tool_call（有 id 和 name）
                                if (tc.has("id")) {
                                    state.id = tc.get("id").asText();
                                }
                                if (tc.has("function") && tc.get("function").has("name")) {
                                    state.name = tc.get("function").get("name").asText();
                                }

                                // 如果刚拿到 id+name，发送 output_item.added
                                if (state.id != null && state.name != null && !state.started) {
                                    state.started = true;
                                    state.outputIndex = outputIndex.getAndIncrement();
                                    String itemId = "fc_" + state.id;
                                    state.itemId = itemId;

                                    ObjectNode itemAdded = mapper.createObjectNode();
                                    itemAdded.put("type", "response.output_item.added");
                                    itemAdded.put("output_index", state.outputIndex);
                                    ObjectNode itemObj = mapper.createObjectNode();
                                    itemObj.put("id", itemId);
                                    itemObj.put("type", "function_call");
                                    itemObj.put("status", "in_progress");
                                    itemObj.put("call_id", state.id);
                                    itemObj.put("name", state.name);
                                    itemObj.put("arguments", "");
                                    itemAdded.set("item", itemObj);
                                    events.add(sse("response.output_item.added", itemAdded.toString()));
                                }

                                // arguments delta
                                if (tc.has("function") && tc.get("function").has("arguments")) {
                                    String argDelta = tc.get("function").get("arguments").asText();
                                    if (argDelta != null && !argDelta.isEmpty()) {
                                        state.arguments.append(argDelta);

                                        ObjectNode argEvent = mapper.createObjectNode();
                                        argEvent.put("type", "response.function_call_arguments.delta");
                                        argEvent.put("item_id", state.itemId != null ? state.itemId : "fc_" + tcIndex);
                                        argEvent.put("output_index", state.outputIndex);
                                        argEvent.put("delta", argDelta);
                                        events.add(sse("response.function_call_arguments.delta", argEvent.toString()));
                                    }
                                }
                            }
                        }
                    }
                }
                return events;
            } catch (Exception e) {
                log.debug("Failed to parse OpenAI Chat stream event: {}", data);
                return java.util.Collections.<ServerSentEvent<String>>emptyList();
            }
        });
    }

    private static class ToolCallState {
        String id;
        String name;
        String itemId;
        int outputIndex;
        boolean started = false;
        StringBuilder arguments = new StringBuilder();
    }

    private static class ReasoningState {
        String itemId;
        int outputIndex;
        boolean started = false;
        boolean hasToolCall = false;
        StringBuilder content = new StringBuilder();
    }

    private java.util.List<ServerSentEvent<String>> buildStartEvents(String responseId) {
        java.util.List<ServerSentEvent<String>> events = new java.util.ArrayList<>();

        ObjectNode created = mapper.createObjectNode();
        created.put("type", "response.created");
        ObjectNode respObj = mapper.createObjectNode();
        respObj.put("id", responseId);
        respObj.put("object", "response");
        respObj.put("status", "in_progress");
        created.set("response", respObj);
        events.add(sse("response.created", created.toString()));

        ObjectNode inProgress = mapper.createObjectNode();
        inProgress.put("type", "response.in_progress");
        inProgress.set("response", respObj);
        events.add(sse("response.in_progress", inProgress.toString()));

        return events;
    }

    private java.util.List<ServerSentEvent<String>> buildTextItemStart(int outputIdx) {
        java.util.List<ServerSentEvent<String>> events = new java.util.ArrayList<>();

        ObjectNode itemAdded = mapper.createObjectNode();
        itemAdded.put("type", "response.output_item.added");
        itemAdded.put("output_index", outputIdx);
        ObjectNode itemObj = mapper.createObjectNode();
        itemObj.put("id", "msg_0");
        itemObj.put("type", "message");
        itemObj.put("role", "assistant");
        itemObj.put("status", "in_progress");
        itemObj.set("content", mapper.createArrayNode());
        itemAdded.set("item", itemObj);
        events.add(sse("response.output_item.added", itemAdded.toString()));

        ObjectNode partAdded = mapper.createObjectNode();
        partAdded.put("type", "response.content_part.added");
        partAdded.put("item_id", "msg_0");
        partAdded.put("output_index", outputIdx);
        partAdded.put("content_index", 0);
        ObjectNode partObj = mapper.createObjectNode();
        partObj.put("type", "output_text");
        partObj.put("text", "");
        partAdded.set("part", partObj);
        events.add(sse("response.content_part.added", partAdded.toString()));

        return events;
    }

    private java.util.List<ServerSentEvent<String>> buildDoneEvents(
            String responseId, StringBuilder fullText, boolean hasText, int textOutputIdx,
            java.util.concurrent.ConcurrentHashMap<Integer, ToolCallState> toolCallStates,
            ReasoningState reasoningState) {

        java.util.List<ServerSentEvent<String>> endEvents = new java.util.ArrayList<>();

        if (reasoningState.started) {
            ObjectNode reasoningDone = mapper.createObjectNode();
            reasoningDone.put("type", "response.output_item.done");
            reasoningDone.put("output_index", reasoningState.outputIndex);
            boolean includeContent = reasoningState.content.length() > 0;
            ObjectNode item = buildReasoningOutputItem(reasoningState.content.toString(), includeContent);
            item.put("id", reasoningState.itemId);
            item.put("status", "completed");
            reasoningDone.set("item", item);
            endEvents.add(sse("response.output_item.done", reasoningDone.toString()));
        }

        // 关闭文本 output item
        if (hasText) {
            ObjectNode textDone = mapper.createObjectNode();
            textDone.put("type", "response.output_text.done");
            textDone.put("item_id", "msg_0");
            textDone.put("output_index", textOutputIdx);
            textDone.put("content_index", 0);
            textDone.put("text", fullText.toString());
            endEvents.add(sse("response.output_text.done", textDone.toString()));

            ObjectNode partDone = mapper.createObjectNode();
            partDone.put("type", "response.content_part.done");
            partDone.put("item_id", "msg_0");
            partDone.put("output_index", textOutputIdx);
            partDone.put("content_index", 0);
            ObjectNode partObj = mapper.createObjectNode();
            partObj.put("type", "output_text");
            partObj.put("text", fullText.toString());
            partDone.set("part", partObj);
            endEvents.add(sse("response.content_part.done", partDone.toString()));

            ObjectNode itemDone = mapper.createObjectNode();
            itemDone.put("type", "response.output_item.done");
            itemDone.put("output_index", textOutputIdx);
            ObjectNode itemObj = mapper.createObjectNode();
            itemObj.put("id", "msg_0");
            itemObj.put("type", "message");
            itemObj.put("role", "assistant");
            itemObj.put("status", "completed");
            ArrayNode contentArr = mapper.createArrayNode();
            ObjectNode textContent = mapper.createObjectNode();
            textContent.put("type", "output_text");
            textContent.put("text", fullText.toString());
            contentArr.add(textContent);
            itemObj.set("content", contentArr);
            itemDone.set("item", itemObj);
            endEvents.add(sse("response.output_item.done", itemDone.toString()));
        }

        // 关闭每个 tool_call output item
        for (ToolCallState state : toolCallStates.values()) {
            if (!state.started) continue;

            // function_call_arguments.done
            ObjectNode argsDone = mapper.createObjectNode();
            argsDone.put("type", "response.function_call_arguments.done");
            argsDone.put("item_id", state.itemId);
            argsDone.put("output_index", state.outputIndex);
            argsDone.put("arguments", state.arguments.toString());
            endEvents.add(sse("response.function_call_arguments.done", argsDone.toString()));

            // output_item.done
            ObjectNode itemDone = mapper.createObjectNode();
            itemDone.put("type", "response.output_item.done");
            itemDone.put("output_index", state.outputIndex);
            ObjectNode itemObj = mapper.createObjectNode();
            itemObj.put("id", state.itemId);
            itemObj.put("type", "function_call");
            itemObj.put("status", "completed");
            itemObj.put("call_id", state.id);
            itemObj.put("name", state.name);
            itemObj.put("arguments", state.arguments.toString());
            itemDone.set("item", itemObj);
            endEvents.add(sse("response.output_item.done", itemDone.toString()));
        }

        // response.completed
        ObjectNode completed = mapper.createObjectNode();
        completed.put("type", "response.completed");
        ObjectNode respObj = mapper.createObjectNode();
        respObj.put("id", responseId);
        respObj.put("object", "response");
        respObj.put("status", "completed");
        completed.set("response", respObj);
        endEvents.add(sse("response.completed", completed.toString()));

        return endEvents;
    }

    private ServerSentEvent<String> sse(String eventType, String data) {
        return ServerSentEvent.<String>builder().event(eventType).data(data).build();
    }

    private boolean isReasoningItem(JsonNode item) {
        return item.has("type") && "reasoning".equals(item.get("type").asText());
    }

    private boolean isFunctionCallItem(JsonNode item) {
        return item.has("type") && "function_call".equals(item.get("type").asText());
    }

    private ObjectNode buildAssistantToolCallsMessage(ArrayNode toolCalls, String reasoningContent) {
        ObjectNode msg = mapper.createObjectNode();
        msg.put("role", "assistant");
        msg.putNull("content");
        attachReasoningContent(msg, reasoningContent);
        msg.set("tool_calls", toolCalls);
        return msg;
    }

    private void attachReasoningContent(ObjectNode msg, String reasoningContent) {
        if (!"assistant".equals(msg.path("role").asText())) return;
        if (reasoningContent == null || reasoningContent.isBlank()) return;
        msg.put("reasoning_content", reasoningContent);
    }

    private ObjectNode convertFunctionCallToToolCall(JsonNode item) {
        ObjectNode tc = mapper.createObjectNode();
        tc.put("id", item.has("call_id") ? item.get("call_id").asText() : "");
        tc.put("type", "function");
        ObjectNode func = mapper.createObjectNode();
        func.put("name", item.has("name") ? item.get("name").asText() : "");
        func.put("arguments", item.has("arguments") ? item.get("arguments").asText() : "");
        tc.set("function", func);
        return tc;
    }

    private String mergeReasoningContent(String current, String next) {
        if (next == null || next.isBlank()) return current;
        if (current == null || current.isBlank()) return next;
        return current + next;
    }

    private String extractReasoningContent(JsonNode item) {
        String rawReasoning = textValue(item, "reasoning_content");
        if (!rawReasoning.isBlank()) return rawReasoning;

        String encoded = textValue(item, "encrypted_content");
        if (encoded.startsWith(DEEPSEEK_REASONING_PREFIX)) {
            return decodeReasoningContent(encoded);
        }

        StringBuilder fallback = new StringBuilder();
        appendReasoningTextParts(item.get("summary"), fallback);
        appendReasoningTextParts(item.get("content"), fallback);
        return fallback.toString();
    }

    private void appendReasoningTextParts(JsonNode node, StringBuilder target) {
        if (node == null || node.isNull()) return;
        if (node.isTextual()) {
            target.append(node.asText());
            return;
        }
        if (!node.isArray()) return;
        for (JsonNode part : node) {
            if (part.isTextual()) {
                target.append(part.asText());
            } else if (part.has("text")) {
                target.append(part.get("text").asText());
            }
        }
    }

    private ObjectNode buildReasoningOutputItem(String reasoningContent, boolean includeContent) {
        ObjectNode item = mapper.createObjectNode();
        item.put("id", "rs_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24));
        item.put("type", "reasoning");
        item.put("status", includeContent ? "completed" : "in_progress");
        item.set("summary", mapper.createArrayNode());
        if (includeContent && reasoningContent != null && !reasoningContent.isBlank()) {
            item.put("encrypted_content", encodeReasoningContent(reasoningContent));
        }
        return item;
    }

    private String encodeReasoningContent(String reasoningContent) {
        String payload = Base64.getEncoder().encodeToString(reasoningContent.getBytes(StandardCharsets.UTF_8));
        return DEEPSEEK_REASONING_PREFIX + payload;
    }

    private String decodeReasoningContent(String encoded) {
        try {
            String payload = encoded.substring(DEEPSEEK_REASONING_PREFIX.length());
            return new String(Base64.getDecoder().decode(payload), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private String textValue(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName) || node.get(fieldName).isNull()) return "";
        return node.get(fieldName).asText();
    }

    private JsonNode convertResponsesContentToChatContent(JsonNode content) {
        StringBuilder textOnly = new StringBuilder();
        ArrayNode chatParts = mapper.createArrayNode();
        boolean hasNonText = false;

        for (JsonNode part : content) {
            String text = textValue(part, "text");
            if (text.isBlank()) text = textValue(part, "input_text");
            if (text.isBlank()) text = textValue(part, "output_text");
            if (!text.isBlank()) {
                textOnly.append(text);
                ObjectNode textPart = mapper.createObjectNode();
                textPart.put("type", "text");
                textPart.put("text", text);
                chatParts.add(textPart);
                continue;
            }

            if (part.has("image_url")) {
                String imageUrl = part.get("image_url").asText();
                if (!imageUrl.isBlank()) {
                    hasNonText = true;
                    ObjectNode imagePart = mapper.createObjectNode();
                    imagePart.put("type", "image_url");
                    ObjectNode imageUrlObject = mapper.createObjectNode();
                    imageUrlObject.put("url", imageUrl);
                    imagePart.set("image_url", imageUrlObject);
                    chatParts.add(imagePart);
                }
            }
        }

        if (!hasNonText) {
            return mapper.getNodeFactory().textNode(textOnly.toString());
        }
        return chatParts;
    }
}
