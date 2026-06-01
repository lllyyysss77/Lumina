package com.lumina.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lumina.dto.RequestLogDetailDto;
import com.lumina.dto.RequestLogPayloadDto;
import com.lumina.entity.RequestLog;
import com.lumina.mapper.RequestLogMapper;
import com.lumina.service.RequestLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;

@Service
public class RequestLogServiceImpl extends ServiceImpl<RequestLogMapper, RequestLog> implements RequestLogService {

    @Override
    @Transactional
    public void saveBatchLogs(Collection<RequestLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return;
        }
        this.saveBatch(new ArrayList<>(logs), 200);
    }

    @Override
    public RequestLogDetailDto getDetailMetaById(String id) {
        RequestLog log = this.getOne(new LambdaQueryWrapper<RequestLog>()
                .eq(RequestLog::getId, id)
                .select(
                        RequestLog::getId,
                        RequestLog::getRequestId,
                        RequestLog::getRequestTime,
                        RequestLog::getRequestType,
                        RequestLog::getRequestModelName,
                        RequestLog::getActualModelName,
                        RequestLog::getProviderId,
                        RequestLog::getProviderName,
                        RequestLog::getIsStream,
                        RequestLog::getInputTokens,
                        RequestLog::getOutputTokens,
                        RequestLog::getFirstTokenTime,
                        RequestLog::getFirstTokenMs,
                        RequestLog::getTotalTime,
                        RequestLog::getTotalTimeMs,
                        RequestLog::getCost,
                        RequestLog::getStatus,
                        RequestLog::getErrorStage,
                        RequestLog::getErrorMessage,
                        RequestLog::getRetryCount,
                        RequestLog::getRequestIp,
                        RequestLog::getProtocolConversion,
                        RequestLog::getCreatedAt
                ));
        if (log == null) {
            return null;
        }

        RequestLogDetailDto dto = new RequestLogDetailDto();
        dto.setId(log.getId());
        dto.setRequestId(log.getRequestId());
        dto.setRequestTime(log.getRequestTime());
        dto.setRequestType(log.getRequestType());
        dto.setRequestModelName(log.getRequestModelName());
        dto.setActualModelName(log.getActualModelName());
        dto.setProviderId(log.getProviderId());
        dto.setProviderName(log.getProviderName());
        dto.setIsStream(log.getIsStream());
        dto.setInputTokens(log.getInputTokens());
        dto.setOutputTokens(log.getOutputTokens());
        dto.setCacheReadTokens(log.getCacheReadTokens());
        dto.setCacheCreationTokens(log.getCacheCreationTokens());
        dto.setFirstTokenTime(log.getFirstTokenTime());
        dto.setFirstTokenMs(log.getFirstTokenMs());
        dto.setTotalTime(log.getTotalTime());
        dto.setTotalTimeMs(log.getTotalTimeMs());
        dto.setCost(log.getCost());
        dto.setStatus(log.getStatus());
        dto.setErrorStage(log.getErrorStage());
        dto.setErrorMessage(log.getErrorMessage());
        dto.setRetryCount(log.getRetryCount());
        dto.setRequestIp(log.getRequestIp());
        dto.setProtocolConversion(log.getProtocolConversion());
        dto.setCreatedAt(log.getCreatedAt());
        return dto;
    }

    @Override
    public RequestLogPayloadDto getPayloadsById(String id) {
        RequestLog log = this.getOne(new LambdaQueryWrapper<RequestLog>()
                .eq(RequestLog::getId, id)
                .select(
                        RequestLog::getId,
                        RequestLog::getRequestContent,
                        RequestLog::getResponseContent
                ));
        if (log == null) {
            return null;
        }

        RequestLogPayloadDto dto = new RequestLogPayloadDto();
        dto.setId(log.getId());
        dto.setRequestContent(log.getRequestContent());
        dto.setResponseContent(log.getResponseContent());
        return dto;
    }

    @Override
    @Transactional
    public int deleteLogsOlderThan(long timestamp) {
        LambdaQueryWrapper<RequestLog> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.lt(RequestLog::getRequestTime, timestamp);
        long count = this.count(queryWrapper);
        if (count > 0) {
            this.remove(queryWrapper);
        }
        return (int) count;
    }

    @Override
    @Transactional
    public int clearContentOlderThan(long timestamp) {
        return this.baseMapper.clearContentBefore(timestamp);
    }
}
