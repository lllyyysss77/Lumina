package com.lumina.mapper;

import com.lumina.dto.DashboardOverviewDto;
import com.lumina.dto.ModelTokenUsageDto;
import com.lumina.dto.ProviderStatsDto;
import com.lumina.dto.RequestTrafficDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DashboardMapper {

    /**
     * 获取全部仪表盘概览统计数据（无时间限制）
     */
    @Select("SELECT " +
            "COUNT(*) as totalRequests, " +
            "COALESCE(SUM(cost), 0) as totalCost, " +
            "COALESCE(AVG(total_time_ms), 0) as avgLatency, " +
            "COALESCE(SUM(CASE WHEN status = 'success' THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*), 0), 0) as successRate " +
            "FROM request_logs")
    DashboardOverviewDto getAllOverviewStats();

    /**
     * 获取指定日期范围的统计数据（用于计算当天/前一天数据）
     */
    @Select("SELECT " +
            "COUNT(*) as totalRequests, " +
            "COALESCE(SUM(cost), 0) as totalCost, " +
            "COALESCE(AVG(total_time_ms), 0) as avgLatency, " +
            "COALESCE(SUM(CASE WHEN status = 'success' THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*), 0), 0) as successRate " +
            "FROM request_logs " +
            "WHERE created_at >= #{startTime} AND created_at < #{endTime}")
    DashboardOverviewDto getDateRangeStats(@Param("startTime") String startTime, @Param("endTime") String endTime);

    /**
     * 获取24小时请求流量数据
     */
    @Select("SELECT " +
            "HOUR(created_at) as hour, " +
            "COUNT(*) as requestCount, " +
            "UNIX_TIMESTAMP(DATE_FORMAT(MIN(created_at), '%Y-%m-%d %H:00:00')) * 1000 as timestamp " +
            "FROM request_logs " +
            "WHERE created_at >= #{startTime} " +
            "GROUP BY HOUR(created_at), DATE_FORMAT(created_at, '%Y-%m-%d %H:00:00') " +
            "ORDER BY hour")
    List<RequestTrafficDto> getRequestTraffic(@Param("startTime") String startTime);

    /**
     * 获取模型 Token 使用统计
     */
    @Select("SELECT " +
            "actual_model_name as modelName, " +
            "COALESCE(SUM(input_tokens), 0) as inputTokens, " +
            "COALESCE(SUM(output_tokens), 0) as outputTokens, " +
            "COALESCE(SUM(input_tokens + output_tokens), 0) as totalTokens, " +
            "COUNT(*) as requestCount " +
            "FROM request_logs " +
            "WHERE created_at >= #{startTime} AND actual_model_name IS NOT NULL " +
            "GROUP BY actual_model_name " +
            "ORDER BY totalTokens DESC " +
            "LIMIT 10")
    List<ModelTokenUsageDto> getModelTokenUsage(@Param("startTime") String startTime);

    /**
     * 获取供应商统计排名
     */
    @Select("SELECT " +
            "provider_id as providerId, " +
            "provider_name as providerName, " +
            "COUNT(*) as callCount, " +
            "COALESCE(SUM(cost), 0) as estimatedCost, " +
            "COALESCE(AVG(total_time_ms), 0) as avgLatency, " +
            "COALESCE(SUM(CASE WHEN status = 'success' THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*), 0), 0) as successRate " +
            "FROM request_logs " +
            "GROUP BY provider_id, provider_name " +
            "ORDER BY callCount DESC " +
            "LIMIT #{limit}")
    List<ProviderStatsDto> getProviderStats(@Param("limit") Integer limit);
}
