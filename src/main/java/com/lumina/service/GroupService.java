package com.lumina.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lumina.dto.ModelGroupConfig;
import com.lumina.entity.Group;
import reactor.core.publisher.Mono;

public interface GroupService extends IService<Group> {

    /**
     * 分页获取分组
     * @param page
     * @param name 分组名称（模糊查询）
     * @return
     */
    Page<Group> getGroupsByPage(Page<Object> page, String name);

    /**
     * 根据id获取分组
     * @param id
     * @return
     */
    Group getGroupById(Long id);

    /**
     * 根据模型名称获取模型分组
     * @param model
     * @return
     */
    ModelGroupConfig getModelGroupConfig(String model);

    Mono<ModelGroupConfig> getModelGroupConfigAsync(String model);

    /**
     * 创建分组
     * @param group
     * @return
     */
    void createGroup(Group group);

    /**
     * 更新分组
     * @param group
     * @return
     */
    void updateGroup(Long id,Group group);

    /**
     * 删除分组
     * @param id
     * @return
     */
    void deleteGroup(Long id);
}
