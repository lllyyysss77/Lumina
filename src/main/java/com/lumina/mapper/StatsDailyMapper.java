package com.lumina.mapper;

import com.lumina.entity.StatsDaily;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface StatsDailyMapper {

    void upsert(@Param("statDate") String statDate,
                @Param("providerId") Long providerId,
                @Param("providerName") String providerName,
                @Param("modelName") String modelName,
                @Param("requests") long requests,
                @Param("successCount") long successCount,
                @Param("inputTokens") long inputTokens,
                @Param("outputTokens") long outputTokens,
                @Param("cost") BigDecimal cost,
                @Param("latencyMs") long latencyMs,
                @Param("cacheReadTokens") long cacheReadTokens,
                @Param("cacheCreationTokens") long cacheCreationTokens,
                @Param("cacheHitCount") long cacheHitCount);

    List<StatsDaily> selectByDateRange(@Param("startDate") String startDate,
                                       @Param("endDate") String endDate);

    List<StatsDaily> selectProviderStats(@Param("limit") int limit);

    StatsDaily selectGlobalTotal();

    StatsDaily selectGlobalByDate(@Param("statDate") String statDate);

    void deleteAll();
}
