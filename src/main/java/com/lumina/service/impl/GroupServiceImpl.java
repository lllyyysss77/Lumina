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
import java.util.Objects;

@Service
public class GroupServiceImpl extends ServiceImpl<GroupMapper, Group> implements GroupService {

    @Autowired
    private HotPathCacheService hotPathCacheService;

    @Autowired
    private GroupItemService groupItemService;

    @Override
    public Page<Group> getGroupsByPage(Page<Object> page) {
        return baseMapper.getGroupsByPage(page);
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
        // 删除旧的关联
        groupItemService.remove(new LambdaQueryWrapper<GroupItem>()
                .eq(GroupItem::getGroupId, id));
        // 批量插入新的关联
        group.getGroupItems().forEach(item -> item.setGroupId(group.getId()));
        groupItemService.saveBatch(group.getGroupItems());
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
