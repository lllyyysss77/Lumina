package com.lumina.mapper;

import com.lumina.dto.DashboardOverviewDto;
import com.lumina.dto.ModelTokenUsageDto;
import com.lumina.dto.ProviderStatsDto;
import com.lumina.dto.RequestTrafficDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DashboardMapper {

    /**
     * 获取全部仪表盘概览统计数据（无时间限制）
     */
    DashboardOverviewDto getAllOverviewStats();

    /**
     * 获取指定日期范围的统计数据（用于计算当天/前一天数据）
     */
    DashboardOverviewDto getDateRangeStats(@Param("startTime") String startTime, @Param("endTime") String endTime);

    /**
     * 获取24小时请求流量数据
     */
    List<RequestTrafficDto> getRequestTraffic(@Param("startTime") String startTime);

    /**
     * 获取模型 Token 使用统计
     */
    List<ModelTokenUsageDto> getModelTokenUsage(@Param("startTime") String startTime);

    /**
     * 获取供应商统计排名
     */
    List<ProviderStatsDto> getProviderStats(@Param("limit") Integer limit);
}
