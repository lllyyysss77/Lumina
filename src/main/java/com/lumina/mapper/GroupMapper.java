package com.lumina.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumina.dto.ModelGroupConfig;
import com.lumina.entity.Group;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GroupMapper extends BaseMapper<Group> {
    /**
     * 根据名称获取模型分组
     * @param modelGroupName
     * @return
     */
    ModelGroupConfig getModelGroupByName(String modelGroupName);

    /**
     * 获取模型分组列表
     * @param page
     * @param name 分组名称（模糊查询）
     * @return
     */
    Page<Group> getGroupsByPage(Page<Object> page, @Param("name") String name);
}
