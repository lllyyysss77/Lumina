package com.lumina.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lumina.entity.MigrationRecord;
import com.lumina.mapper.MigrationRecordMapper;
import com.lumina.service.MigrationRecordService;
import org.springframework.stereotype.Service;

@Service
public class MigrationRecordServiceImpl extends ServiceImpl<MigrationRecordMapper, MigrationRecord> implements MigrationRecordService {
}
