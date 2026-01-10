package com.lumina.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lumina.entity.StatsTotal;
import com.lumina.mapper.StatsTotalMapper;
import com.lumina.service.StatsTotalService;
import org.springframework.stereotype.Service;

@Service
public class StatsTotalServiceImpl extends ServiceImpl<StatsTotalMapper, StatsTotal> implements StatsTotalService {
}
