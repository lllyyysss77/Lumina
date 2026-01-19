package com.lumina.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumina.entity.LlmModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LlmModelMapper extends BaseMapper<LlmModel> {
    /**
     * 分页查询
     *
     * @param page
     * @param queryWrapper
     * @return
     */
    Page<LlmModel> queryPage(@Param("page") Page<Object> page, @Param(Constants.WRAPPER) LambdaQueryWrapper<LlmModel> queryWrapper);

}
