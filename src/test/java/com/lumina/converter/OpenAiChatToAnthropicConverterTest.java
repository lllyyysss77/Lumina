package com.lumina.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenAiChatToAnthropicConverterTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final OpenAiChatToAnthropicConverter converter = new OpenAiChatToAnthropicConverter();

    @Test
    void convertRequestMapsOpenAiImageUrlToAnthropicImageBlock() throws Exception {
        ObjectNode request = (ObjectNode) mapper.readTree("""
                {
                  "model": "claude-sonnet-4-5",
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
        JsonNode content = converted.get("messages").get(0).get("content");

        assertEquals("text", content.get(0).get("type").asText());
        assertEquals("image", content.get(1).get("type").asText());
        assertEquals("base64", content.get(1).get("source").get("type").asText());
        assertEquals("image/png", content.get(1).get("source").get("media_type").asText());
        assertEquals("AAA=", content.get(1).get("source").get("data").asText());
    }

    @Test
    void streamResponseKeepsToolArgumentDeltasOnTheirOwnToolCallIndexes() throws Exception {
        List<ServerSentEvent<String>> events = converter.convertStreamResponse(Flux.just(
                sse("{\"type\":\"message_start\",\"message\":{\"model\":\"claude-sonnet-4-5\"}}"),
                sse("{\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"tool_use\",\"id\":\"tool_a\",\"name\":\"first\",\"input\":{}}}"),
                sse("{\"type\":\"content_block_start\",\"index\":1,\"content_block\":{\"type\":\"tool_use\",\"id\":\"tool_b\",\"name\":\"second\",\"input\":{}}}"),
                sse("{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"a\\\":1}\"}}"),
                sse("{\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"b\\\":2}\"}}"),
                sse("{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"tool_use\"}}"),
                sse("{\"type\":\"message_stop\"}")
        )).collectList().block();

        JsonNode firstDelta = nthToolCallDelta(events, 2);
        JsonNode secondDelta = nthToolCallDelta(events, 3);

        assertEquals(0, firstDelta.get("index").asInt());
        assertEquals("{\"a\":1}", firstDelta.get("function").get("arguments").asText());
        assertEquals(1, secondDelta.get("index").asInt());
        assertEquals("{\"b\":2}", secondDelta.get("function").get("arguments").asText());
    }

    private ServerSentEvent<String> sse(String data) {
        return ServerSentEvent.<String>builder().data(data).build();
    }

    private JsonNode nthToolCallDelta(List<ServerSentEvent<String>> events, int ordinal) throws Exception {
        int seen = 0;
        for (ServerSentEvent<String> event : events) {
            if (event.data() == null || "[DONE]".equals(event.data())) continue;
            JsonNode node = mapper.readTree(event.data());
            JsonNode toolCalls = node.path("choices").path(0).path("delta").path("tool_calls");
            if (toolCalls.isArray() && toolCalls.size() > 0) {
                if (seen == ordinal) {
                    return toolCalls.get(0);
                }
                seen++;
            }
        }
        throw new AssertionError("Missing tool call delta #" + ordinal);
    }
}
