package com.lumina.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lumina.entity.LlmModel;
import com.lumina.mapper.LlmModelMapper;
import com.lumina.service.LlmModelService;
import org.springframework.stereotype.Service;

@Service
public class LlmModelServiceImpl extends ServiceImpl<LlmModelMapper, LlmModel> implements LlmModelService {
}
