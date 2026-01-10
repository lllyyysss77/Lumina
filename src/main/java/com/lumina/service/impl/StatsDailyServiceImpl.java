package com.lumina.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lumina.entity.StatsDaily;
import com.lumina.mapper.StatsDailyMapper;
import com.lumina.service.StatsDailyService;
import org.springframework.stereotype.Service;

@Service
public class StatsDailyServiceImpl extends ServiceImpl<StatsDailyMapper, StatsDaily> implements StatsDailyService {
}
