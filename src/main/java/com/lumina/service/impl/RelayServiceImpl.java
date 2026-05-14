package com.lumina.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lumina.converter.ProtocolConverter;
import com.lumina.converter.ProtocolConverterRegistry;
import com.lumina.converter.ProtocolType;
import com.lumina.dto.ModelGroupConfig;
import com.lumina.dto.ModelGroupConfigItem;
import com.lumina.entity.Group;
import com.lumina.service.FailoverService;
import com.lumina.service.GroupService;
import com.lumina.service.LlmRequestExecutor;
import com.lumina.service.RelayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
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

    @Autowired
    private ProtocolConverterRegistry converterRegistry;

    private LlmRequestExecutor getExecutor(String type) {
        return executors.stream()
                .filter(e -> e.supports(type))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("不支持的请求类型: " + type));
    }

    @Override
    public Mono<ResponseEntity<?>> relay(String type, ObjectNode params, Map<String, String> queryParams, String apiKey) {
        String modelGroupName = params.get("model").asText();
        log.info("Relaying request for model group: {}", modelGroupName);
        Map<String, String> enrichedParams = new java.util.HashMap<>(queryParams);
        if (apiKey != null) {
            enrichedParams.put("_lumina_api_key", apiKey);
        }
        return groupService.getModelGroupConfigAsync(modelGroupName)
                .switchIfEmpty(Mono.error(new RuntimeException("模型分组不存在")))
                .flatMap(modelGroupConfig -> {
                    if (modelGroupConfig == null) {
                        return Mono.error(new RuntimeException("模型分组不存在"));
                    }

                    Integer timeoutMs = modelGroupConfig.getFirstTokenTimeout();
                    boolean stream = params.has("stream") && params.get("stream").asBoolean();
                    ProtocolType inboundType = ProtocolType.fromRequestType(type);

                    if (stream) {
                        Flux<?> body = failoverService.executeWithFailoverFlux(
                                (provider) -> {
                                    ProtocolType outboundType = ProtocolType.fromCode(provider.getProviderType());
                                    Optional<ProtocolConverter> converter = converterRegistry.getConverter(inboundType, outboundType);

                                    ObjectNode requestParams = params.deepCopy();
                                    requestParams.put("model", provider.getModelName());

                                    ObjectNode finalRequest = converter.map(c -> c.convertRequest(requestParams)).orElse(requestParams);
                                    String executorType = converter.isPresent() ? outboundType.toRequestType() : type;
                                    LlmRequestExecutor executor = getExecutor(executorType);

                                    if (converter.isPresent()) {
                                        log.info("协议转换 [{}→{}], 转换后请求: {}", inboundType, outboundType, finalRequest);
                                    }

                                    Flux<ServerSentEvent<String>> upstream = executor.executeStream(
                                            finalRequest, provider, enrichedParams, "", executorType, timeoutMs
                                    );

                                    return converter.map(c -> c.convertStreamResponse(upstream)).orElse(upstream);
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
                                ProtocolType outboundType = ProtocolType.fromCode(provider.getProviderType());
                                Optional<ProtocolConverter> converter = converterRegistry.getConverter(inboundType, outboundType);

                                ObjectNode requestParams = params.deepCopy();
                                requestParams.put("model", provider.getModelName());

                                ObjectNode finalRequest = converter.map(c -> c.convertRequest(requestParams)).orElse(requestParams);
                                String executorType = converter.isPresent() ? outboundType.toRequestType() : type;
                                LlmRequestExecutor executor = getExecutor(executorType);

                                if (converter.isPresent()) {
                                    log.info("协议转换 [{}→{}], 转换后请求: {}", inboundType, outboundType, finalRequest);
                                }

                                return executor.executeNormal(
                                        finalRequest, provider, enrichedParams, "", executorType, timeoutMs
                                ).map(resp -> converter.map(c -> c.convertResponse(resp)).orElse(resp));
                            },
                            modelGroupConfig,
                            timeoutMs
                    ).map(ResponseEntity::ok);
                });
    }

    @Override
    public Mono<ResponseEntity<?>> relay(String type, String modelAction, ObjectNode params, Map<String, String> queryParams, String apiKey) {
        String[] parts = modelAction.split(":", 2);
        String modelGroupName = parts[0];
        String action = parts.length > 1 ? parts[1] : "";
        Map<String, String> enrichedParams = new java.util.HashMap<>(queryParams);
        if (apiKey != null) {
            enrichedParams.put("_lumina_api_key", apiKey);
        }

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
                                            enrichedParams,
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
                                        enrichedParams,
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
                        model.put("context_length", 1050000);
                        model.put("max_completion_tokens", 128000);
                        dataArray.add(model);
                    }

                    ObjectNode response = objectMapper.createObjectNode();
                    response.put("object", "list");
                    response.set("data", dataArray);

                    return ResponseEntity.ok(response);
                });
    }
}
