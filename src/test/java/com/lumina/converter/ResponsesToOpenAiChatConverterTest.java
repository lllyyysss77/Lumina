package com.lumina.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponsesToOpenAiChatConverterTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ResponsesToOpenAiChatConverter converter = new ResponsesToOpenAiChatConverter();

    @Test
    void convertRequestRestoresReasoningContentForFunctionCallHistory() throws Exception {
        ObjectNode request = (ObjectNode) mapper.readTree("""
                {
                  "model": "deepseek-v4-flash",
                  "input": [
                    {
                      "type": "reasoning",
                      "id": "rs_1",
                      "reasoning_content": "Need to inspect the workspace."
                    },
                    {
                      "type": "function_call",
                      "call_id": "call_1",
                      "name": "exec_command",
                      "arguments": "{\\"cmd\\":\\"pwd\\"}"
                    },
                    {
                      "type": "function_call",
                      "call_id": "call_2",
                      "name": "exec_command",
                      "arguments": "{\\"cmd\\":\\"ls\\"}"
                    },
                    {
                      "type": "function_call_output",
                      "call_id": "call_1",
                      "output": "/home/jojo/projects/java/lumina"
                    },
                    {
                      "type": "function_call_output",
                      "call_id": "call_2",
                      "output": "pom.xml"
                    },
                    {
                      "type": "message",
                      "role": "user",
                      "content": [{"type": "input_text", "text": "continue"}]
                    }
                  ]
                }
                """);

        ObjectNode converted = converter.convertRequest(request);
        JsonNode assistant = converted.get("messages").get(0);

        assertEquals("assistant", assistant.get("role").asText());
        assertEquals("Need to inspect the workspace.", assistant.get("reasoning_content").asText());
        assertEquals("call_1", assistant.get("tool_calls").get(0).get("id").asText());
        assertEquals("call_2", assistant.get("tool_calls").get(1).get("id").asText());
        assertEquals("tool", converted.get("messages").get(1).get("role").asText());
        assertEquals("tool", converted.get("messages").get(2).get("role").asText());
        assertEquals("continue", converted.get("messages").get(3).get("content").asText());
    }

    @Test
    void convertRequestRestoresReasoningContentForAssistantTextHistory() throws Exception {
        ObjectNode request = (ObjectNode) mapper.readTree("""
                {
                  "model": "deepseek-v4-flash",
                  "input": [
                    {
                      "type": "reasoning",
                      "encrypted_content": "deepseek-reasoning:TmVlZCB0byBpbnNwZWN0IGZpcnN0Lg=="
                    },
                    {
                      "type": "message",
                      "role": "assistant",
                      "content": [{"type": "output_text", "text": "Let me inspect the workspace."}]
                    },
                    {
                      "type": "message",
                      "role": "user",
                      "content": [{"type": "input_text", "text": "continue"}]
                    }
                  ]
                }
                """);

        ObjectNode converted = converter.convertRequest(request);
        JsonNode assistant = converted.get("messages").get(0);

        assertEquals("assistant", assistant.get("role").asText());
        assertEquals("Need to inspect first.", assistant.get("reasoning_content").asText());
        assertEquals("Let me inspect the workspace.", assistant.get("content").asText());
        assertEquals("user", converted.get("messages").get(1).get("role").asText());
    }

    @Test
    void convertResponsePreservesReasoningContentWhenAssistantCallsTool() throws Exception {
        ObjectNode response = (ObjectNode) mapper.readTree("""
                {
                  "id": "chatcmpl_1",
                  "model": "deepseek-v4-flash",
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "content": null,
                        "reasoning_content": "Need to run pwd.",
                        "tool_calls": [
                          {
                            "id": "call_1",
                            "type": "function",
                            "function": {
                              "name": "exec_command",
                              "arguments": "{\\"cmd\\":\\"pwd\\"}"
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
                """);

        ObjectNode converted = converter.convertResponse(response);
        JsonNode output = converted.get("output");

        assertEquals("reasoning", output.get(0).get("type").asText());
        assertTrue(output.get(0).get("encrypted_content").asText().startsWith("deepseek-reasoning:"));
        assertEquals("function_call", output.get(1).get("type").asText());

        ObjectNode followUp = mapper.createObjectNode();
        followUp.put("model", "deepseek-v4-flash");
        ArrayNode input = mapper.createArrayNode();
        input.add(output.get(0).deepCopy());
        input.add(output.get(1).deepCopy());
        followUp.set("input", input);

        ObjectNode roundTripped = converter.convertRequest(followUp);
        assertEquals("Need to run pwd.", roundTripped.get("messages").get(0).get("reasoning_content").asText());
    }

    @Test
    void convertResponsePreservesReasoningContentWhenAssistantReturnsText() throws Exception {
        ObjectNode response = (ObjectNode) mapper.readTree("""
                {
                  "id": "chatcmpl_1",
                  "model": "deepseek-v4-flash",
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "content": "Let me inspect the workspace.",
                        "reasoning_content": "Need to inspect first."
                      }
                    }
                  ]
                }
                """);

        ObjectNode converted = converter.convertResponse(response);
        JsonNode output = converted.get("output");

        assertEquals("reasoning", output.get(0).get("type").asText());
        assertTrue(output.get(0).get("encrypted_content").asText().startsWith("deepseek-reasoning:"));
        assertEquals("message", output.get(1).get("type").asText());

        ObjectNode followUp = mapper.createObjectNode();
        followUp.put("model", "deepseek-v4-flash");
        ArrayNode input = mapper.createArrayNode();
        input.add(output.get(0).deepCopy());
        input.add(output.get(1).deepCopy());
        followUp.set("input", input);

        ObjectNode roundTripped = converter.convertRequest(followUp);
        JsonNode assistant = roundTripped.get("messages").get(0);
        assertEquals("Need to inspect first.", assistant.get("reasoning_content").asText());
        assertEquals("Let me inspect the workspace.", assistant.get("content").asText());
    }

    @Test
    void streamResponseRoundTripsReasoningContentThroughEncryptedContent() throws Exception {
        List<ServerSentEvent<String>> events = converter.convertStreamResponse(Flux.just(
                sse("{\"choices\":[{\"delta\":{\"reasoning_content\":\"Need \"}}]}"),
                sse("{\"choices\":[{\"delta\":{\"reasoning_content\":\"tools.\"}}]}"),
                sse("{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"exec_command\",\"arguments\":\"\"}}]}}]}"),
                sse("{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"{\\\"cmd\\\":\\\"pwd\\\"}\"}}]}}]}"),
                sse("[DONE]")
        )).collectList().block();

        assertNotNull(events);
        ObjectNode reasoningItem = findDoneItem(events, "reasoning");
        assertTrue(reasoningItem.get("encrypted_content").asText().startsWith("deepseek-reasoning:"));

        ObjectNode followUp = mapper.createObjectNode();
        followUp.put("model", "deepseek-v4-flash");
        ArrayNode input = mapper.createArrayNode();
        input.add(reasoningItem);
        ObjectNode functionCall = mapper.createObjectNode();
        functionCall.put("type", "function_call");
        functionCall.put("call_id", "call_1");
        functionCall.put("name", "exec_command");
        functionCall.put("arguments", "{\"cmd\":\"pwd\"}");
        input.add(functionCall);
        followUp.set("input", input);

        ObjectNode converted = converter.convertRequest(followUp);
        JsonNode assistant = converted.get("messages").get(0);

        assertEquals("Need tools.", assistant.get("reasoning_content").asText());
    }

    @Test
    void streamResponsePreservesTextOnlyReasoningContentThroughEncryptedContent() throws Exception {
        List<ServerSentEvent<String>> events = converter.convertStreamResponse(Flux.just(
                sse("{\"choices\":[{\"delta\":{\"reasoning_content\":\"Need \"}}]}"),
                sse("{\"choices\":[{\"delta\":{\"reasoning_content\":\"answer.\"}}]}"),
                sse("{\"choices\":[{\"delta\":{\"content\":\"Done.\"}}]}"),
                sse("[DONE]")
        )).collectList().block();

        assertNotNull(events);
        ObjectNode reasoningItem = findDoneItem(events, "reasoning");
        ObjectNode messageItem = findDoneItem(events, "message");
        assertTrue(reasoningItem.get("encrypted_content").asText().startsWith("deepseek-reasoning:"));

        ObjectNode followUp = mapper.createObjectNode();
        followUp.put("model", "deepseek-v4-flash");
        ArrayNode input = mapper.createArrayNode();
        input.add(reasoningItem);
        input.add(messageItem);
        followUp.set("input", input);

        ObjectNode converted = converter.convertRequest(followUp);
        JsonNode assistant = converted.get("messages").get(0);

        assertEquals("Need answer.", assistant.get("reasoning_content").asText());
        assertEquals("Done.", assistant.get("content").asText());
    }

    private ServerSentEvent<String> sse(String data) {
        return ServerSentEvent.<String>builder().data(data).build();
    }

    private ObjectNode findDoneItem(List<ServerSentEvent<String>> events, String type) throws Exception {
        for (ServerSentEvent<String> event : events) {
            if (!"response.output_item.done".equals(event.event()) || event.data() == null) {
                continue;
            }
            JsonNode node = mapper.readTree(event.data());
            JsonNode item = node.get("item");
            if (item != null && type.equals(item.path("type").asText())) {
                return (ObjectNode) item;
            }
        }
        throw new AssertionError("Missing done item with type " + type);
    }
}
