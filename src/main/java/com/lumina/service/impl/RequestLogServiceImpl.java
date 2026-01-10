package com.lumina.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lumina.entity.RequestLog;
import com.lumina.mapper.RequestLogMapper;
import com.lumina.service.RequestLogService;
import org.springframework.stereotype.Service;

@Service
public class RequestLogServiceImpl extends ServiceImpl<RequestLogMapper, RequestLog> implements RequestLogService {
}
