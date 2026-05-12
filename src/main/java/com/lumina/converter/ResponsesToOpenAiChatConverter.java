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
public class ResponsesToOpenAiChatConverter implements ProtocolConverter {

    private static final ObjectMapper mapper = new ObjectMapper();

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
                for (JsonNode item : input) {
                    if (item.isTextual()) {
                        ObjectNode msg = mapper.createObjectNode();
                        msg.put("role", "user");
                        msg.put("content", item.asText());
                        messages.add(msg);
                    } else if (item.isObject()) {
                        ObjectNode converted = convertInputItem(item);
                        if (converted != null) messages.add(converted);
                    }
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
            result.set("tool_choice", request.get("tool_choice"));
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
                StringBuilder sb = new StringBuilder();
                for (JsonNode part : content) {
                    if (part.has("text")) sb.append(part.get("text").asText());
                    else if (part.has("input_text")) sb.append(part.get("input_text").asText());
                }
                msg.put("content", sb.toString());
            } else {
                msg.set("content", content);
            }
        }
        return msg;
    }

    private ObjectNode convertInputItem(JsonNode item) {
        String type = item.has("type") ? item.get("type").asText() : "";

        switch (type) {
            case "function_call" -> {
                // function_call → assistant message with tool_calls
                ObjectNode msg = mapper.createObjectNode();
                msg.put("role", "assistant");
                msg.putNull("content");
                ArrayNode toolCalls = mapper.createArrayNode();
                ObjectNode tc = mapper.createObjectNode();
                tc.put("id", item.has("call_id") ? item.get("call_id").asText() : "");
                tc.put("type", "function");
                ObjectNode func = mapper.createObjectNode();
                func.put("name", item.has("name") ? item.get("name").asText() : "");
                func.put("arguments", item.has("arguments") ? item.get("arguments").asText() : "");
                tc.set("function", func);
                toolCalls.add(tc);
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
        StringBuilder fullText = new StringBuilder();
        // 跟踪每个 tool_call 的状态: index → {id, name, arguments}
        java.util.concurrent.ConcurrentHashMap<Integer, ToolCallState> toolCallStates = new java.util.concurrent.ConcurrentHashMap<>();
        java.util.concurrent.atomic.AtomicBoolean hasTextOutput = new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicBoolean textItemStarted = new java.util.concurrent.atomic.AtomicBoolean(false);

        return upstream.flatMapIterable(event -> {
            String data = event.data();
            if (data == null || data.isBlank()) return java.util.Collections.<ServerSentEvent<String>>emptyList();

            if ("[DONE]".equals(data)) {
                return buildDoneEvents(responseId, fullText, hasTextOutput.get(), toolCallStates, outputIndex);
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

                        // 处理文本内容
                        if (delta.has("content") && !delta.get("content").isNull()) {
                            if (!textItemStarted.getAndSet(true)) {
                                hasTextOutput.set(true);
                                events.addAll(buildTextItemStart(outputIndex.getAndIncrement()));
                            }
                            String text = delta.get("content").asText();
                            fullText.append(text);

                            ObjectNode deltaEvent = mapper.createObjectNode();
                            deltaEvent.put("type", "response.output_text.delta");
                            deltaEvent.put("item_id", "msg_0");
                            deltaEvent.put("output_index", 0);
                            deltaEvent.put("content_index", 0);
                            deltaEvent.put("delta", text);
                            events.add(sse("response.output_text.delta", deltaEvent.toString()));
                        }

                        // 处理 tool_calls
                        if (delta.has("tool_calls") && delta.get("tool_calls").isArray()) {
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
            String responseId, StringBuilder fullText, boolean hasText,
            java.util.concurrent.ConcurrentHashMap<Integer, ToolCallState> toolCallStates,
            java.util.concurrent.atomic.AtomicInteger outputIndex) {

        java.util.List<ServerSentEvent<String>> endEvents = new java.util.ArrayList<>();

        // 关闭文本 output item
        if (hasText) {
            ObjectNode textDone = mapper.createObjectNode();
            textDone.put("type", "response.output_text.done");
            textDone.put("item_id", "msg_0");
            textDone.put("output_index", 0);
            textDone.put("content_index", 0);
            textDone.put("text", fullText.toString());
            endEvents.add(sse("response.output_text.done", textDone.toString()));

            ObjectNode partDone = mapper.createObjectNode();
            partDone.put("type", "response.content_part.done");
            partDone.put("item_id", "msg_0");
            partDone.put("output_index", 0);
            partDone.put("content_index", 0);
            ObjectNode partObj = mapper.createObjectNode();
            partObj.put("type", "output_text");
            partObj.put("text", fullText.toString());
            partDone.set("part", partObj);
            endEvents.add(sse("response.content_part.done", partDone.toString()));

            ObjectNode itemDone = mapper.createObjectNode();
            itemDone.put("type", "response.output_item.done");
            itemDone.put("output_index", 0);
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
}
