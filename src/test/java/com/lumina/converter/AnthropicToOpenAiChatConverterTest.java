package com.lumina.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

    @Test
    void streamResponseDoesNotStartEmptyTextBlockBeforeToolCall() throws Exception {
        List<ServerSentEvent<String>> events = converter.convertStreamResponse(Flux.just(
                sse("{\"id\":\"chatcmpl_1\",\"object\":\"chat.completion.chunk\",\"model\":\"deepseek-v4-pro\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"\"},\"finish_reason\":null}]}"),
                sse("{\"id\":\"chatcmpl_1\",\"object\":\"chat.completion.chunk\",\"model\":\"deepseek-v4-pro\",\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"TaskCreate\",\"arguments\":\"\"}}]},\"finish_reason\":null}]}"),
                sse("{\"id\":\"chatcmpl_1\",\"object\":\"chat.completion.chunk\",\"model\":\"deepseek-v4-pro\",\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"{\\\"description\\\":\"}}]},\"finish_reason\":null}]}"),
                sse("{\"id\":\"chatcmpl_1\",\"object\":\"chat.completion.chunk\",\"model\":\"deepseek-v4-pro\",\"choices\":[{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"\\\"继续搜索\\\"}\"}}]},\"finish_reason\":null}]}"),
                sse("[DONE]")
        )).collectList().block();

        List<JsonNode> blockStarts = events.stream()
                .filter(event -> "content_block_start".equals(event.event()))
                .map(event -> {
                    try {
                        return mapper.readTree(event.data());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();

        assertEquals(1, blockStarts.size());
        assertEquals(0, blockStarts.get(0).get("index").asInt());
        assertEquals("tool_use", blockStarts.get(0).get("content_block").get("type").asText());
        assertFalse(events.stream().anyMatch(event -> event.data() != null && event.data().contains("text_delta")));
    }

    private ServerSentEvent<String> sse(String data) {
        return ServerSentEvent.<String>builder().data(data).build();
    }
}
