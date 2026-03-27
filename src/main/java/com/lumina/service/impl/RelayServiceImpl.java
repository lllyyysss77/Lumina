package com.lumina.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lumina.dto.ModelGroupConfig;
import com.lumina.dto.ModelGroupConfigItem;
import com.lumina.entity.Group;
import com.lumina.service.FailoverService;
import com.lumina.service.GroupService;
import com.lumina.service.LlmRequestExecutor;
import com.lumina.service.RelayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
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

    @Autowired
    private ObjectMapper objectMapper;

    private LlmRequestExecutor getExecutor(String type) {
        return executors.stream()
                .filter(e -> e.supports(type))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("不支持的请求类型: " + type));
    }

    @Override
    public Mono<ResponseEntity<?>> relay(String type, ObjectNode params, Map<String, String> queryParams) {
        String modelGroupName = params.get("model").asText();
        return groupService.getModelGroupConfigAsync(modelGroupName)
                .switchIfEmpty(Mono.error(new RuntimeException("模型分组不存在")))
                .flatMap(modelGroupConfig -> {
                    if (modelGroupConfig == null) {
                        return Mono.error(new RuntimeException("模型分组不存在"));
                    }

                    Integer timeoutMs = modelGroupConfig.getFirstTokenTimeout();
                    boolean stream = params.has("stream") && params.get("stream").asBoolean();
                    LlmRequestExecutor executor = getExecutor(type);

                    if (stream) {
                        Flux<?> body = failoverService.executeWithFailoverFlux(
                                (provider) -> {
                                    ObjectNode requestParams = params.deepCopy();
                                    requestParams.put("model", provider.getModelName());
                                    return executor.executeStream(
                                            requestParams,
                                            provider,
                                            queryParams,
                                            "",
                                            type,
                                            timeoutMs
                                    );
                                },
                                modelGroupConfig,
                                timeoutMs
                        );

                        return Mono.just(ResponseEntity.ok()
                                .contentType(MediaType.TEXT_EVENT_STREAM)
                                .body(body));
                    }

                    return failoverService.executeWithFailoverMono(
                            (provider) -> {
                                ObjectNode requestParams = params.deepCopy();
                                requestParams.put("model", provider.getModelName());
                                return executor.executeNormal(
                                        requestParams,
                                        provider,
                                        queryParams,
                                        "",
                                        type,
                                        timeoutMs
                                );
                            },
                            modelGroupConfig,
                            timeoutMs
                    ).map(ResponseEntity::ok);
                });
    }

    @Override
    public Mono<ResponseEntity<?>> relay(String type, String modelAction, ObjectNode params, Map<String, String> queryParams) {
        String[] parts = modelAction.split(":", 2);
        String modelGroupName = parts[0];
        String action = parts.length > 1 ? parts[1] : "";

        return groupService.getModelGroupConfigAsync(modelGroupName)
                .switchIfEmpty(Mono.error(new RuntimeException("模型分组不存在")))
                .flatMap(modelGroupConfig -> {
                    if (modelGroupConfig == null) {
                        return Mono.error(new RuntimeException("模型分组不存在"));
                    }

                    Integer timeoutMs = modelGroupConfig.getFirstTokenTimeout();
                    boolean stream = action.equalsIgnoreCase("streamGenerateContent");
                    LlmRequestExecutor executor = getExecutor(type);

                    if (stream) {
                        Flux<String> body = failoverService.executeWithFailoverFlux(
                                (provider) -> {
                                    ObjectNode requestParams = params.deepCopy();
                                    return executor.executeStream(
                                            requestParams,
                                            provider,
                                            queryParams,
                                            provider.getModelName() + ":" + action,
                                            type,
                                            timeoutMs
                                    );
                                },
                                modelGroupConfig,
                                timeoutMs
                        ).map(sse -> " " + sse.data());

                        return Mono.just(ResponseEntity.ok()
                                .contentType(MediaType.TEXT_EVENT_STREAM)
                                .body(body));
                    }

                    return failoverService.executeWithFailoverMono(
                            (provider) -> {
                                ObjectNode requestParams = params.deepCopy();
                                return executor.executeNormal(
                                        requestParams,
                                        provider,
                                        queryParams,
                                        provider.getModelName() + ":" + action,
                                        type,
                                        timeoutMs
                                );
                            },
                            modelGroupConfig,
                            timeoutMs
                    ).map(ResponseEntity::ok);
                });
    }

    @Override
    public Mono<ResponseEntity<?>> models() {
        return Mono.fromCallable(groupService::list)
                .subscribeOn(Schedulers.boundedElastic())
                .map(groups -> {
                    ArrayNode dataArray = objectMapper.createArrayNode();
                    long createdTimestamp = Instant.now().getEpochSecond();

                    for (Group group : groups) {
                        ObjectNode model = objectMapper.createObjectNode();
                        model.put("id", group.getName());
                        model.put("object", "model");
                        model.put("created", createdTimestamp);
                        model.put("owned_by", "OpenAI");
                        dataArray.add(model);
                    }

                    ObjectNode response = objectMapper.createObjectNode();
                    response.put("object", "list");
                    response.set("data", dataArray);

                    return ResponseEntity.ok(response);
                });
    }
}
