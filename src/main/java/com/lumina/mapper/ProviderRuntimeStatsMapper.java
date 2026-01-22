package com.lumina.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumina.entity.ProviderRuntimeStats;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;

@Mapper
public interface ProviderRuntimeStatsMapper extends BaseMapper<ProviderRuntimeStats> {

    int upsert(ProviderRuntimeStats stats);

    /**
     * 删除指定的 Provider 运行态数据
     * @param providerId Provider ID
     * @return 删除的记录数
     */
    int deleteByProviderId(@Param("providerId") String providerId);

    /**
     * 批量删除不在指定列表中的 Provider 运行态数据
     * @param validProviderIds 有效的 Provider ID 列表
     * @return 删除的记录数
     */
    int deleteNotInProviderIds(@Param("validProviderIds") Collection<String> validProviderIds);
}
