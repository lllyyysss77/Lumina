package com.lumina.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lumina.entity.LlmModel;

public interface LlmModelService extends IService<LlmModel> {
    void syncModels();

    /**
     * 分页查询
     *
     * @param page
     * @param queryWrapper
     * @return
     */
    Page<LlmModel> queryPage(Page<Object> page, LambdaQueryWrapper<LlmModel> queryWrapper);
}
