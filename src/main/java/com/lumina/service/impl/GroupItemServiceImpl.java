package com.lumina.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lumina.entity.GroupItem;
import com.lumina.mapper.GroupItemMapper;
import com.lumina.service.GroupItemService;
import org.springframework.stereotype.Service;

@Service
public class GroupItemServiceImpl extends ServiceImpl<GroupItemMapper, GroupItem> implements GroupItemService {
}
