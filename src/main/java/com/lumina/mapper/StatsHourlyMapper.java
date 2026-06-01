package com.lumina.mapper;

import com.lumina.entity.StatsHourly;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface StatsHourlyMapper {

    void upsert(@Param("statHour") String statHour,
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

    List<StatsHourly> selectByHourRange(@Param("startHour") String startHour,
                                         @Param("endHour") String endHour);

    List<StatsHourly> selectModelUsageByHourRange(@Param("startHour") String startHour,
                                                   @Param("endHour") String endHour,
                                                   @Param("limit") int limit);

    List<StatsHourly> selectAggregatedByHourRange(@Param("startHour") String startHour,
                                                    @Param("endHour") String endHour);

    void deleteAll();

    void deleteBefore(@Param("beforeHour") String beforeHour);
}
