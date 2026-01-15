package com.lumina.service.impl;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lumina.dto.ModelGroupConfig;
import com.lumina.dto.ModelGroupConfigItem;
import com.lumina.service.FailoverService;
import com.lumina.service.GroupService;
import com.lumina.service.LlmRequestExecutor;
import com.lumina.service.RelayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Service
public class RelayServiceImpl implements RelayService {

    @Autowired
    private GroupService groupService;

    @Autowired
    private FailoverService failoverService;

    @Autowired
    private List<LlmRequestExecutor> executors;

    private LlmRequestExecutor getExecutor(String type) {
        return executors.stream()
                .filter(e -> e.supports(type))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("不支持的请求类型: " + type));
    }

    @Override
    public Mono<ResponseEntity<?>> relay(String type, ObjectNode params, Map<String, String> queryParams) {
        String modelGroupName = params.get("model").asText();
        ModelGroupConfig modelGroupConfig = groupService.getModelGroupConfig(modelGroupName);
        if (modelGroupConfig == null) {
            return Mono.error(new RuntimeException("模型分组不存在"));
        }

        boolean stream = params.has("stream") && params.get("stream").asBoolean();
        LlmRequestExecutor executor = getExecutor(type);

        if (stream) {
            // 返回 Mono<ResponseEntity<Flux<...>>>
            // 这会告诉 WebFlux：响应头是 text/event-stream，Body 是一个流
            return Mono.just(
                    ResponseEntity.ok()
                            .contentType(MediaType.TEXT_EVENT_STREAM)
                            .body(
                                    failoverService.executeWithFailoverFlux(
                                            () -> {
                                                ModelGroupConfigItem provider = failoverService.selectAvailableProvider(modelGroupConfig);
                                                ObjectNode requestParams = params.deepCopy();
                                                requestParams.put("model", provider.getModelName());
                                                return executor.executeStream(
                                                        requestParams,
                                                        provider,
                                                        queryParams,
                                                        "",
                                                        type
                                                );
                                            },
                                            modelGroupConfig
                                    )
                            )
            );
        } else {
            // 返回 Mono<ResponseEntity<ObjectNode>>
            return failoverService.executeWithFailoverMono(
                    () -> {
                        ModelGroupConfigItem provider = failoverService.selectAvailableProvider(modelGroupConfig);
                        ObjectNode requestParams = params.deepCopy();
                        requestParams.put("model", provider.getModelName());
                        return executor.executeNormal(
                                requestParams,
                                provider,
                                queryParams,
                                "",
                                type
                        );
                    },
                    modelGroupConfig
            ).map(ResponseEntity::ok);
        }
    }

    @Override
    public Mono<ResponseEntity<?>> relay(String type, String modelAction, ObjectNode params, Map<String, String> queryParams) {
        String[] parts = modelAction.split(":", 2);
        String modelGroupName = parts[0];
        String action = parts.length > 1 ? parts[1] : "";

        ModelGroupConfig modelGroupConfig = groupService.getModelGroupConfig(modelGroupName);
        if (modelGroupConfig == null) {
            return Mono.error(new RuntimeException("模型分组不存在"));
        }

        boolean stream = action.equalsIgnoreCase("streamGenerateContent");
        LlmRequestExecutor executor = getExecutor(type);

        if (stream) {
            // 返回 Mono<ResponseEntity<Flux<...>>>
            // 这会告诉 WebFlux：响应头是 text/event-stream，Body 是一个流
            return Mono.just(
                    ResponseEntity.ok()
                            .contentType(MediaType.TEXT_EVENT_STREAM)
                            .body(
                                    failoverService.executeWithFailoverFlux(
                                            () -> {
                                                ModelGroupConfigItem provider = failoverService.selectAvailableProvider(modelGroupConfig);
                                                ObjectNode requestParams = params.deepCopy();
                                                return executor.executeStream(
                                                        requestParams,
                                                        provider,
                                                        queryParams,
                                                        provider.getModelName() + ":" + action,
                                                        type
                                                );
                                            },
                                            modelGroupConfig
                                    )
                            )
            );
        } else {
            // 返回 Mono<ResponseEntity<ObjectNode>>
            return failoverService.executeWithFailoverMono(
                    () -> {
                        ModelGroupConfigItem provider = failoverService.selectAvailableProvider(modelGroupConfig);
                        ObjectNode requestParams = params.deepCopy();
                        return executor.executeNormal(
                                requestParams,
                                provider,
                                queryParams,
                                provider.getModelName() + ":" + action,
                                type
                        );
                    },
                    modelGroupConfig
            ).map(ResponseEntity::ok);
        }
    }
}
