package com.lumina.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lumina.entity.StatsHourly;
import com.lumina.mapper.StatsHourlyMapper;
import com.lumina.service.StatsHourlyService;
import org.springframework.stereotype.Service;

@Service
public class StatsHourlyServiceImpl extends ServiceImpl<StatsHourlyMapper, StatsHourly> implements StatsHourlyService {
}
