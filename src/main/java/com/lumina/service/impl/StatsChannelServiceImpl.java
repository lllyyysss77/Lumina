package com.lumina.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lumina.entity.StatsChannel;
import com.lumina.mapper.StatsChannelMapper;
import com.lumina.service.StatsChannelService;
import org.springframework.stereotype.Service;

@Service
public class StatsChannelServiceImpl extends ServiceImpl<StatsChannelMapper, StatsChannel> implements StatsChannelService {
}
