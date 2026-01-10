package com.lumina.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumina.entity.RequestLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RequestLogMapper extends BaseMapper<RequestLog> {
}
