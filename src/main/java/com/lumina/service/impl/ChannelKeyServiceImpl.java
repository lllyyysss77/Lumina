package com.lumina.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lumina.entity.ChannelKey;
import com.lumina.mapper.ChannelKeyMapper;
import com.lumina.service.ChannelKeyService;
import org.springframework.stereotype.Service;

@Service
public class ChannelKeyServiceImpl extends ServiceImpl<ChannelKeyMapper, ChannelKey> implements ChannelKeyService {
}
