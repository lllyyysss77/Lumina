package com.lumina.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lumina.dto.RequestLogDetailDto;
import com.lumina.dto.RequestLogPayloadDto;
import com.lumina.entity.RequestLog;

import java.util.Collection;

public interface RequestLogService extends IService<RequestLog> {
    void saveBatchLogs(Collection<RequestLog> logs);

    RequestLogDetailDto getDetailMetaById(String id);

    RequestLogPayloadDto getPayloadsById(String id);
}
