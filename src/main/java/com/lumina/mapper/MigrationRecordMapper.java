package com.lumina.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumina.entity.MigrationRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MigrationRecordMapper extends BaseMapper<MigrationRecord> {
}
