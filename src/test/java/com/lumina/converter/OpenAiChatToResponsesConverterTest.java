package com.lumina.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiChatToResponsesConverterTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final OpenAiChatToResponsesConverter converter = new OpenAiChatToResponsesConverter();

    @Test
    void convertRequestPreservesAssistantReasoningBeforeToolCalls() throws Exception {
        ObjectNode request = (ObjectNode) mapper.readTree("""
                {
                  "model": "deepseek-v4-flash",
                  "messages": [
                    {
                      "role": "assistant",
                      "content": null,
                      "reasoning_content": "Need to inspect files.",
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
                  ]
                }
                """);

        ObjectNode converted = converter.convertRequest(request);
        JsonNode input = converted.get("input");

        assertEquals("reasoning", input.get(0).get("type").asText());
        assertTrue(input.get(0).get("encrypted_content").asText().startsWith("deepseek-reasoning:"));
        assertEquals("function_call", input.get(1).get("type").asText());
        assertEquals("call_1", input.get(1).get("call_id").asText());
    }

    @Test
    void convertRequestPreservesAssistantReasoningBeforeTextMessage() throws Exception {
        ObjectNode request = (ObjectNode) mapper.readTree("""
                {
                  "model": "deepseek-v4-flash",
                  "messages": [
                    {
                      "role": "assistant",
                      "content": "Let me inspect the workspace.",
                      "reasoning_content": "Need to inspect first."
                    }
                  ]
                }
                """);

        ObjectNode converted = converter.convertRequest(request);
        JsonNode input = converted.get("input");

        assertEquals("reasoning", input.get(0).get("type").asText());
        assertTrue(input.get(0).get("encrypted_content").asText().startsWith("deepseek-reasoning:"));
        assertEquals("message", input.get(1).get("type").asText());
        assertEquals("assistant", input.get(1).get("role").asText());
        assertEquals("Let me inspect the workspace.", input.get(1).get("content").get(0).get("text").asText());
    }

    @Test
    void convertResponseRestoresOwnEncodedReasoningForChatToolCalls() throws Exception {
        ObjectNode request = (ObjectNode) mapper.readTree("""
                {
                  "model": "deepseek-v4-flash",
                  "messages": [
                    {
                      "role": "assistant",
                      "content": null,
                      "reasoning_content": "Need to inspect files.",
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
                  ]
                }
                """);
        ObjectNode convertedRequest = converter.convertRequest(request);
        ArrayNode input = (ArrayNode) convertedRequest.get("input");

        ObjectNode response = mapper.createObjectNode();
        response.put("id", "resp_1");
        response.put("model", "deepseek-v4-flash");
        ArrayNode output = mapper.createArrayNode();
        output.add(input.get(0).deepCopy());
        output.add(input.get(1).deepCopy());
        response.set("output", output);

        ObjectNode convertedResponse = converter.convertResponse(response);
        JsonNode message = convertedResponse.get("choices").get(0).get("message");

        assertEquals("Need to inspect files.", message.get("reasoning_content").asText());
        assertEquals("call_1", message.get("tool_calls").get(0).get("id").asText());
    }

    @Test
    void convertResponseRestoresOwnEncodedReasoningForChatText() throws Exception {
        ObjectNode request = (ObjectNode) mapper.readTree("""
                {
                  "model": "deepseek-v4-flash",
                  "messages": [
                    {
                      "role": "assistant",
                      "content": "Let me inspect the workspace.",
                      "reasoning_content": "Need to inspect first."
                    }
                  ]
                }
                """);
        ObjectNode convertedRequest = converter.convertRequest(request);
        ArrayNode input = (ArrayNode) convertedRequest.get("input");

        ObjectNode response = mapper.createObjectNode();
        response.put("id", "resp_1");
        response.put("model", "deepseek-v4-flash");
        ArrayNode output = mapper.createArrayNode();
        output.add(input.get(0).deepCopy());
        output.add(input.get(1).deepCopy());
        response.set("output", output);

        ObjectNode convertedResponse = converter.convertResponse(response);
        JsonNode message = convertedResponse.get("choices").get(0).get("message");

        assertEquals("Need to inspect first.", message.get("reasoning_content").asText());
        assertEquals("Let me inspect the workspace.", message.get("content").asText());
    }

    @Test
    void convertRequestMapsOpenAiImageContentToResponsesInputImage() throws Exception {
        ObjectNode request = (ObjectNode) mapper.readTree("""
                {
                  "model": "gpt-4.1",
                  "messages": [
                    {
                      "role": "user",
                      "content": [
                        {"type": "text", "text": "describe"},
                        {"type": "image_url", "image_url": {"url": "data:image/png;base64,AAA="}}
                      ]
                    }
                  ]
                }
                """);

        ObjectNode converted = converter.convertRequest(request);
        JsonNode content = converted.get("input").get(0).get("content");

        assertEquals("input_text", content.get(0).get("type").asText());
        assertEquals("input_image", content.get(1).get("type").asText());
        assertEquals("data:image/png;base64,AAA=", content.get(1).get("image_url").asText());
    }
}
