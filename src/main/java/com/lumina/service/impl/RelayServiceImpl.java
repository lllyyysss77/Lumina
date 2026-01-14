package com.lumina.service.impl;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lumina.common.request.OpenAIChatCompletionsRequest;
import com.lumina.dto.ModelGroupConfig;
import com.lumina.dto.ModelGroupConfigItem;
import com.lumina.service.FailoverService;
import com.lumina.service.GroupService;
import com.lumina.service.ProviderService;
import com.lumina.service.RelayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class RelayServiceImpl implements RelayService {

    @Autowired
    private ProviderService providerService;

    @Autowired
    private GroupService groupService;

    @Autowired
    private FailoverService failoverService;

    @Autowired
    private OpenAIChatCompletionsRequest openAIChatCompletionsRequest;

    @Override
    public Object relay(String type, ObjectNode params, Boolean beta) {
        String modelGroupName = params.get("model").asText();
        ModelGroupConfig modelGroupConfig = groupService.getModelGroupConfig(modelGroupName);
        if (modelGroupConfig == null) {
            return Flux.error(new RuntimeException("模型分组不存在"));
        }

        boolean stream = params.has("stream") && params.get("stream").asBoolean();

        if (stream) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(
                        failoverService.executeWithFailoverFlux(
                            () -> {
                                ModelGroupConfigItem provider = failoverService.selectAvailableProvider(modelGroupConfig);
                                ObjectNode requestParams = params.deepCopy();
                                requestParams.put("model", provider.getModelName());
                                return openAIChatCompletionsRequest.streamChat(
                                        requestParams,
                                        provider.getApiKey(),
                                        provider.getBaseUrl(),
                                        beta,
                                        type);
                            },
                            modelGroupConfig
                        )
                    );
        } else {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(
                        failoverService.executeWithFailoverMono(
                            () -> {
                                ModelGroupConfigItem provider = failoverService.selectAvailableProvider(modelGroupConfig);
                                ObjectNode requestParams = params.deepCopy();
                                requestParams.put("model", provider.getModelName());
                                return openAIChatCompletionsRequest.normalChat(
                                        requestParams,
                                        provider.getApiKey(),
                                        provider.getBaseUrl(),
                                        beta,
                                        type);
                            },
                            modelGroupConfig
                        )
                    );
        }
    }

}
