package com.lumina.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lumina.entity.StatsModel;
import com.lumina.mapper.StatsModelMapper;
import com.lumina.service.StatsModelService;
import org.springframework.stereotype.Service;

@Service
public class StatsModelServiceImpl extends ServiceImpl<StatsModelMapper, StatsModel> implements StatsModelService {
}
