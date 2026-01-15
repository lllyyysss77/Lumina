package com.lumina.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lumina.entity.LlmModel;

public interface LlmModelService extends IService<LlmModel> {
    void syncModels();
}
