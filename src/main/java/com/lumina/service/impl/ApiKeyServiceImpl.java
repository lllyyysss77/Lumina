package com.lumina.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lumina.entity.ApiKey;
import com.lumina.mapper.ApiKeyMapper;
import com.lumina.service.ApiKeyService;
import org.springframework.stereotype.Service;

@Service
public class ApiKeyServiceImpl extends ServiceImpl<ApiKeyMapper, ApiKey> implements ApiKeyService {
}
