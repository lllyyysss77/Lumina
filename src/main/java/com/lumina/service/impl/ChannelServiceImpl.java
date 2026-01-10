package com.lumina.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lumina.entity.Channel;
import com.lumina.mapper.ChannelMapper;
import com.lumina.service.ChannelService;
import org.springframework.stereotype.Service;

@Service
public class ChannelServiceImpl extends ServiceImpl<ChannelMapper, Channel> implements ChannelService {
}
