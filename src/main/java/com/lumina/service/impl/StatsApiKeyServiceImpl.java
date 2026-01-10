package com.lumina.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lumina.entity.StatsApiKey;
import com.lumina.mapper.StatsApiKeyMapper;
import com.lumina.service.StatsApiKeyService;
import org.springframework.stereotype.Service;

@Service
public class StatsApiKeyServiceImpl extends ServiceImpl<StatsApiKeyMapper, StatsApiKey> implements StatsApiKeyService {
}
