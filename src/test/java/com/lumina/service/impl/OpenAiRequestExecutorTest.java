package com.lumina.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lumina.dto.ModelGroupConfigItem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class OpenAiRequestExecutorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void disablesDeepSeekThinkingWhenToolCallHistoryLacksReasoning() throws Exception {
        ObjectNode request = (ObjectNode) mapper.readTree("""
                {
                  "model": "deepseek-v4-flash",
                  "messages": [
                    {
                      "role": "assistant",
                      "content": null,
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
                    },
                    {
                      "role": "tool",
                      "tool_call_id": "call_1",
                      "content": "/home/jojo/projects/java/lumina"
                    }
                  ]
                }
                """);

        OpenAiRequestExecutor.prepareRequestForProvider(request, deepSeekProvider(), "openai_chat_completions");

        assertEquals("disabled", request.get("thinking").get("type").asText());
    }

    @Test
    void disablesDeepSeekThinkingForProxyProviderWhenAssistantTextHistoryLacksReasoning() throws Exception {
        ObjectNode request = (ObjectNode) mapper.readTree("""
                {
                  "model": "deepseek-v4-flash",
                  "messages": [
                    {
                      "role": "assistant",
                      "content": "Let me inspect the workspace."
                    },
                    {
                      "role": "user",
                      "content": "continue"
                    }
                  ]
                }
                """);
        ModelGroupConfigItem provider = new ModelGroupConfigItem();
        provider.setProviderName("opencode");
        provider.setBaseUrl("https://opencode.ai/zen/go");
        provider.setModelName("deepseek-v4-flash");

        OpenAiRequestExecutor.prepareRequestForProvider(request, provider, "openai_chat_completions");

        assertEquals("disabled", request.get("thinking").get("type").asText());
    }

    @Test
    void keepsDeepSeekThinkingDefaultWhenToolCallHistoryHasReasoning() throws Exception {
        ObjectNode request = (ObjectNode) mapper.readTree("""
                {
                  "messages": [
                    {
                      "role": "assistant",
                      "content": null,
                      "reasoning_content": "Need to inspect the workspace.",
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

        OpenAiRequestExecutor.prepareRequestForProvider(request, deepSeekProvider(), "openai_chat_completions");

        assertFalse(request.has("thinking"));
    }

    @Test
    void keepsDeepSeekThinkingDefaultWhenAssistantTextHistoryHasReasoning() throws Exception {
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

        OpenAiRequestExecutor.prepareRequestForProvider(request, deepSeekProvider(), "openai_chat_completions");

        assertFalse(request.has("thinking"));
    }

    @Test
    void doesNotAddThinkingForNonDeepSeekProviders() throws Exception {
        ObjectNode request = (ObjectNode) mapper.readTree("""
                {
                  "messages": [
                    {
                      "role": "assistant",
                      "content": null,
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
        ModelGroupConfigItem provider = new ModelGroupConfigItem();
        provider.setProviderName("OpenAI");
        provider.setBaseUrl("https://api.openai.com");

        OpenAiRequestExecutor.prepareRequestForProvider(request, provider, "openai_chat_completions");

        assertFalse(request.has("thinking"));
    }

    @Test
    void preservesExistingThinkingSetting() throws Exception {
        ObjectNode request = (ObjectNode) mapper.readTree("""
                {
                  "thinking": {"type": "enabled"},
                  "messages": [
                    {
                      "role": "assistant",
                      "content": null,
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

        OpenAiRequestExecutor.prepareRequestForProvider(request, deepSeekProvider(), "openai_chat_completions");

        assertEquals("enabled", request.get("thinking").get("type").asText());
    }

    private ModelGroupConfigItem deepSeekProvider() {
        ModelGroupConfigItem provider = new ModelGroupConfigItem();
        provider.setProviderName("DeepSeek");
        provider.setBaseUrl("https://api.deepseek.com");
        return provider;
    }
}
