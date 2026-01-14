package com.lumina.service.impl;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lumina.common.request.OpenAIChatCompletionsRequest;
import com.lumina.dto.ModelGroupConfig;
import com.lumina.dto.ModelGroupConfigItem;
import com.lumina.service.GroupService;
import com.lumina.service.ProviderService;
import com.lumina.service.RelayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RelayServiceImpl implements RelayService {

    @Autowired
    private ProviderService providerService;

    @Autowired
    private GroupService groupService;

    @Autowired
    private OpenAIChatCompletionsRequest openAIChatCompletionsRequest;

    // 用于轮询算法的计数器，key为groupId，value为当前索引
    private final ConcurrentHashMap<String, AtomicInteger> roundRobinCounters = new ConcurrentHashMap<>();

    @Override
    public Object relay(String type, ObjectNode params, Boolean beta) {
        String modelGroupName = params.get("model").asText();
        ModelGroupConfig modelGroupConfig = groupService.getModelGroupConfig(modelGroupName);
        if (modelGroupConfig == null) {
            return Flux.error(new RuntimeException("模型分组不存在"));
        }
        ModelGroupConfigItem decide = decide(modelGroupConfig);
        params.put("model", decide.getModelName());

        boolean stream = params.has("stream") && params.get("stream").asBoolean();
        if (stream) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(openAIChatCompletionsRequest.streamChat(
                            params,
                            decide.getApiKey(),
                            decide.getBaseUrl(),
                            beta,
                            type));
        } else {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(openAIChatCompletionsRequest.normalChat(
                            params,
                            decide.getApiKey(),
                            decide.getBaseUrl(),
                            beta,
                            type));
        }
    }

    /**
     * 决策
     *
     * @param modelGroupConfig
     * @return
     */
    private ModelGroupConfigItem decide(ModelGroupConfig modelGroupConfig) {
        // 负载均衡模式：1-轮询，2-随机，3-故障转移，4-加权
        Integer balanceMode = modelGroupConfig.getBalanceMode();
        List<ModelGroupConfigItem> groupItems = modelGroupConfig.getItems();

        if (groupItems == null || groupItems.isEmpty()) {
            throw new RuntimeException("模型组中没有可用的模型");
        }

        if (balanceMode == 1) {
            // 轮询算法
            return roundRobinSelect(groupItems, modelGroupConfig.getName());
        }
        return groupItems.get(0);
    }

    /**
     * 轮询选择算法
     *
     * @param groupItems 模型组项目列表
     * @param modelGroupName    组ID
     * @return 选中的模型组项目
     */
    private ModelGroupConfigItem roundRobinSelect(List<ModelGroupConfigItem> groupItems, String modelGroupName) {
        // 获取或创建该组的计数器
        AtomicInteger counter = roundRobinCounters.computeIfAbsent(modelGroupName, k -> new AtomicInteger(0));

        // 原子性地获取并递增计数器
        int index = counter.getAndIncrement() % groupItems.size();

        return groupItems.get(index);
    }
}
