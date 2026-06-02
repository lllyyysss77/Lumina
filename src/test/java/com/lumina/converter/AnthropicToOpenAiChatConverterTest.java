package com.lumina.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnthropicToOpenAiChatConverterTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AnthropicToOpenAiChatConverter converter = new AnthropicToOpenAiChatConverter();

    @Test
    void convertRequestMapsAnthropicImageBlockToOpenAiImageUrlPart() throws Exception {
        ObjectNode request = (ObjectNode) mapper.readTree("""
                {
                  "model": "gpt-4.1",
                  "messages": [
                    {
                      "role": "user",
                      "content": [
                        {"type": "text", "text": "describe"},
                        {
                          "type": "image",
                          "source": {
                            "type": "base64",
                            "media_type": "image/png",
                            "data": "AAA="
                          }
                        }
                      ]
                    }
                  ]
                }
                """);

        ObjectNode converted = converter.convertRequest(request);
        JsonNode content = converted.get("messages").get(0).get("content");

        assertEquals("text", content.get(0).get("type").asText());
        assertEquals("image_url", content.get(1).get("type").asText());
        assertEquals("data:image/png;base64,AAA=", content.get(1).get("image_url").get("url").asText());
    }

    @Test
    void convertRequestKeepsToolResultsBeforeFollowingUserText() throws Exception {
        ObjectNode request = (ObjectNode) mapper.readTree("""
                {
                  "model": "gpt-4.1",
                  "messages": [
                    {
                      "role": "user",
                      "content": [
                        {"type": "tool_result", "tool_use_id": "tool_1", "content": "ok"},
                        {"type": "text", "text": "continue"}
                      ]
                    }
                  ]
                }
                """);

        ObjectNode converted = converter.convertRequest(request);

        assertEquals("tool", converted.get("messages").get(0).get("role").asText());
        assertEquals("tool_1", converted.get("messages").get(0).get("tool_call_id").asText());
        assertEquals("user", converted.get("messages").get(1).get("role").asText());
        assertEquals("continue", converted.get("messages").get(1).get("content").asText());
    }
}
