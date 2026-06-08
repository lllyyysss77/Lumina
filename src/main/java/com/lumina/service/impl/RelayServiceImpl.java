package com.lumina.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
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
import com.lumina.entity.LlmModel;
import com.lumina.service.FailoverService;
import com.lumina.service.GroupService;
import com.lumina.service.LlmModelService;
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

    @Autowired
    private LlmModelService llmModelService;

    private LlmRequestExecutor getExecutor(String type) {
        return executors.stream()
                .filter(e -> e.supports(type))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("不支持的请求类型: " + type));
    }

    @Override
    public Mono<ResponseEntity<?>> relay(String type, ObjectNode params, Map<String, String> queryParams, String apiKey) {
        String modelGroupName = params.get("model").asText();
        log.debug("Relaying request for model group: {}", modelGroupName);
        Map<String, String> enrichedParams = new java.util.HashMap<>(queryParams);
        if (apiKey != null) {
            enrichedParams.put("_lumina_api_key", apiKey);
        }
        enrichedParams.put("_lumina_request_model", modelGroupName);
        return groupService.getModelGroupConfigAsync(modelGroupName)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Model group not found: modelGroupName={}, requestType={}, requestIp={}",
                            modelGroupName, type, enrichedParams.get("_lumina_request_ip"));
                    return Mono.error(new RuntimeException("模型分组不存在: " + modelGroupName));
                }))
                .flatMap(modelGroupConfig -> {
                    if (modelGroupConfig == null) {
                        log.warn("Model group config is null: modelGroupName={}, requestType={}, requestIp={}",
                                modelGroupName, type, enrichedParams.get("_lumina_request_ip"));
                        return Mono.error(new RuntimeException("模型分组不存在: " + modelGroupName));
                    }

                    Integer timeoutMs = modelGroupConfig.getFirstTokenTimeout();
                    boolean stream = params.has("stream") && params.get("stream").asBoolean();
                    ProtocolType inboundType = ProtocolType.fromRequestType(type);

                    if (stream) {
                        Flux<?> body = failoverService.executeWithFailoverFlux(
                                (provider) -> {
                                    ProtocolType outboundType = resolveOutboundType(inboundType, provider);
                                    provider.setBaseUrl(resolveBaseUrl(outboundType, provider));
                                    Optional<ProtocolConverter> converter = converterRegistry.getConverter(inboundType, outboundType);

                                    ObjectNode requestParams = params.deepCopy();
                                    requestParams.put("model", provider.getModelName());

                                    ObjectNode finalRequest = converter.map(c -> c.convertRequest(requestParams)).orElse(requestParams);
                                    String executorType = converter.isPresent() ? outboundType.toRequestType() : type;
                                    LlmRequestExecutor executor = getExecutor(executorType);

                                    Map<String, String> execParams = new java.util.HashMap<>(enrichedParams);
                                    if (converter.isPresent()) {
                                        log.debug("Protocol conversion applied: {}→{}, modelGroup={}, stream=true",
                                                inboundType, outboundType, modelGroupName);
                                        execParams.put("_lumina_protocol_conversion", inboundType.name() + "→" + outboundType.name());
                                    }

                                    Flux<ServerSentEvent<String>> upstream = executor.executeStream(
                                            finalRequest, provider, execParams, "", executorType, timeoutMs
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
                                ProtocolType outboundType = resolveOutboundType(inboundType, provider);
                                provider.setBaseUrl(resolveBaseUrl(outboundType, provider));
                                Optional<ProtocolConverter> converter = converterRegistry.getConverter(inboundType, outboundType);

                                ObjectNode requestParams = params.deepCopy();
                                requestParams.put("model", provider.getModelName());

                                ObjectNode finalRequest = converter.map(c -> c.convertRequest(requestParams)).orElse(requestParams);
                                String executorType = converter.isPresent() ? outboundType.toRequestType() : type;
                                LlmRequestExecutor executor = getExecutor(executorType);

                                Map<String, String> execParams = new java.util.HashMap<>(enrichedParams);
                                if (converter.isPresent()) {
                                    log.debug("Protocol conversion applied: {}→{}, modelGroup={}, stream=false",
                                            inboundType, outboundType, modelGroupName);
                                    execParams.put("_lumina_protocol_conversion", inboundType.name() + "→" + outboundType.name());
                                }

                                return executor.executeNormal(
                                        finalRequest, provider, execParams, "", executorType, timeoutMs
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
        enrichedParams.put("_lumina_request_model", modelGroupName);

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
                        LlmModel model = llmModelService.findLatestByModelName(group.getName());
                        ObjectNode node = objectMapper.createObjectNode();
                        node.put("id", group.getName());
                        node.put("object", "model");
                        node.put("created", createdTimestamp);
                        node.put("owned_by", model != null ? model.getProvider() : "unknown");
                        node.put("context_length", model != null && model.getContextLimit() != null ? model.getContextLimit() : 0);
                        node.put("max_completion_tokens", model != null && model.getOutputLimit() != null ? model.getOutputLimit() : 0);
                        dataArray.add(node);
                    }

                    ObjectNode response = objectMapper.createObjectNode();
                    response.put("object", "list");
                    response.set("data", dataArray);

                    return ResponseEntity.ok(response);
                });
    }

    /**
     * 如果供应商支持入站协议类型，直接使用入站类型，避免不必要的协议转换。
     * 否则回退到配置的默认类型。
     */
    private ProtocolType resolveOutboundType(ProtocolType inboundType, ModelGroupConfigItem provider) {
        String supportedTypes = provider.getSupportedTypes();
        if (supportedTypes != null && !supportedTypes.isEmpty()) {
            int inboundCode = inboundType.getCode();
            for (String code : supportedTypes.split(",")) {
                if (Integer.parseInt(code.trim()) == inboundCode) {
                    return inboundType;
                }
            }
        }
        return ProtocolType.fromCode(provider.getProviderType());
    }

    /**
     * 从 endpointsJson 中查找匹配协议类型的 baseUrl。
     * 如果找不到，回退到 provider 的默认 baseUrl。
     */
    private String resolveBaseUrl(ProtocolType protocolType, ModelGroupConfigItem provider) {
        String endpointsJson = provider.getEndpointsJson();
        if (endpointsJson != null && !endpointsJson.isEmpty()) {
            try {
                Map<String, String> endpoints = objectMapper.readValue(
                        endpointsJson, new TypeReference<Map<String, String>>() {});
                String url = endpoints.get(String.valueOf(protocolType.getCode()));
                if (url != null && !url.isEmpty()) {
                    return url;
                }
            } catch (Exception e) {
                log.warn("Failed to parse endpoints JSON for provider {}: {}",
                        provider.getProviderName(), e.getMessage());
            }
        }
        return provider.getBaseUrl();
    }
}
