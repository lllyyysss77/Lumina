package com.lumina.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumina.entity.ProviderRuntimeStats;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProviderRuntimeStatsMapper extends BaseMapper<ProviderRuntimeStats> {

    @Insert("INSERT INTO provider_runtime_stats (provider_id, provider_name, success_rate_ema, latency_ema_ms, score, total_requests, success_requests, failure_requests, circuit_state, circuit_opened_at, updated_at) " +
            "VALUES (#{providerId}, #{providerName}, #{successRateEma}, #{latencyEmaMs}, #{score}, #{totalRequests}, #{successRequests}, #{failureRequests}, #{circuitState}, #{circuitOpenedAt}, #{updatedAt}) " +
            "ON DUPLICATE KEY UPDATE " +
            "provider_name = VALUES(provider_name), " +
            "success_rate_ema = VALUES(success_rate_ema), " +
            "latency_ema_ms = VALUES(latency_ema_ms), " +
            "score = VALUES(score), " +
            "total_requests = VALUES(total_requests), " +
            "success_requests = VALUES(success_requests), " +
            "failure_requests = VALUES(failure_requests), " +
            "circuit_state = VALUES(circuit_state), " +
            "circuit_opened_at = VALUES(circuit_opened_at), " +
            "updated_at = VALUES(updated_at)")
    int upsert(ProviderRuntimeStats stats);
}
