package com.lumina.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lumina.dto.ModelGroupConfig;
import com.lumina.entity.Group;
import com.lumina.entity.GroupItem;
import com.lumina.mapper.GroupMapper;
import com.lumina.service.GroupItemService;
import com.lumina.service.GroupService;
import com.lumina.service.HotPathCacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class GroupServiceImpl extends ServiceImpl<GroupMapper, Group> implements GroupService {

    @Autowired
    private HotPathCacheService hotPathCacheService;

    @Autowired
    private GroupItemService groupItemService;

    @Override
    public Page<Group> getGroupsByPage(Page<Object> page, String name) {
        return baseMapper.getGroupsByPage(page, name);
    }

    @Override
    public Group getGroupById(Long id) {
        Group group = getById(id);
        if (Objects.isNull(group)) {
            throw new IllegalArgumentException("Group not found with id: " + id);
        }
        group.setGroupItems(groupItemService.list(new LambdaQueryWrapper<GroupItem>()
                .eq(GroupItem::getGroupId, id)));
        return group;
    }

    @Override
    public ModelGroupConfig getModelGroupConfig(String modelGroupName) {
        return loadModelGroupConfig(modelGroupName);
    }

    @Override
    public Mono<ModelGroupConfig> getModelGroupConfigAsync(String modelGroupName) {
        ModelGroupConfig cached = hotPathCacheService.getCachedGroupConfig(modelGroupName);
        if (cached != null) {
            return Mono.just(cached);
        }
        return Mono.fromCallable(() -> loadModelGroupConfig(modelGroupName))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void createGroup(Group group) {
        if (Objects.isNull(group.getGroupItems()) || group.getGroupItems().isEmpty()) {
            throw new IllegalArgumentException("Group items cannot be empty");
        }
        group.setCreatedAt(LocalDateTime.now());
        group.setUpdatedAt(LocalDateTime.now());
        boolean success = save(group);
        if (!success) {
            throw new IllegalArgumentException("Failed to create group");
        }
        group.getGroupItems().forEach(item -> item.setGroupId(group.getId()));
        
        // 过滤掉请求中重复的项 (providerId + modelName)，并使用 LinkedHashMap 保持原有顺序
        List<GroupItem> uniqueItems = group.getGroupItems().stream()
                .collect(Collectors.toMap(
                        item -> item.getProviderId() + "-" + item.getModelName(),
                        item -> item,
                        (existing, replacement) -> replacement,
                        java.util.LinkedHashMap::new
                ))
                .values().stream().toList();
        group.setGroupItems(uniqueItems);
        
        groupItemService.saveBatch(group.getGroupItems());
        hotPathCacheService.invalidateAllGroupConfigs();
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateGroup(Long id,Group group) {
        group.setId(id);
        group.setUpdatedAt(LocalDateTime.now());
        boolean success = updateById(group);
        if (!success) {
            throw new IllegalArgumentException("Group not found with id: " + id);
        }
        
        // 绑定 groupId
        group.getGroupItems().forEach(item -> item.setGroupId(group.getId()));
        
        // 过滤掉请求中重复的项 (providerId + modelName)，并使用 LinkedHashMap 保持原有顺序
        List<GroupItem> uniqueItems = group.getGroupItems().stream()
                .collect(Collectors.toMap(
                        item -> item.getProviderId() + "-" + item.getModelName(),
                        item -> item,
                        (existing, replacement) -> replacement,
                        java.util.LinkedHashMap::new
                ))
                .values().stream().toList();
        group.setGroupItems(uniqueItems);
        
        // 查出当前组的所有已有 item，根据 unique key (providerId + modelName) 匹配并回填 id
        List<GroupItem> existingItems = groupItemService.list(
                new LambdaQueryWrapper<GroupItem>().eq(GroupItem::getGroupId, id)
        );
        Map<String, Long> existingKeyToIdMap = existingItems.stream()
                .collect(Collectors.toMap(
                        item -> item.getProviderId() + "-" + item.getModelName(),
                        GroupItem::getId,
                        (existing, replacement) -> existing
                ));
                
        group.getGroupItems().forEach(item -> {
            String key = item.getProviderId() + "-" + item.getModelName();
            if (existingKeyToIdMap.containsKey(key)) {
                item.setId(existingKeyToIdMap.get(key));
            }
        });

        // 使用 upsert（插入或更新）代替全删全插
        groupItemService.saveOrUpdateBatch(group.getGroupItems());
        
        // 获取并删除本次更新中不存在的旧关联项
        List<Long> currentItemIds = group.getGroupItems().stream()
                .map(GroupItem::getId)
                .filter(Objects::nonNull)
                .toList();
                
        LambdaQueryWrapper<GroupItem> removeWrapper = new LambdaQueryWrapper<GroupItem>()
                .eq(GroupItem::getGroupId, id);
        if (!currentItemIds.isEmpty()) {
            removeWrapper.notIn(GroupItem::getId, currentItemIds);
        }
        groupItemService.remove(removeWrapper);
        
        hotPathCacheService.invalidateAllGroupConfigs();
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteGroup(Long id) {
        removeById(id);
        groupItemService.remove(new LambdaQueryWrapper<GroupItem>()
                .eq(GroupItem::getGroupId, id));
        hotPathCacheService.invalidateAllGroupConfigs();
    }

    private ModelGroupConfig loadModelGroupConfig(String modelGroupName) {
        return hotPathCacheService.getGroupConfig(
                modelGroupName,
                () -> baseMapper.getModelGroupByName(modelGroupName)
        );
    }
}
