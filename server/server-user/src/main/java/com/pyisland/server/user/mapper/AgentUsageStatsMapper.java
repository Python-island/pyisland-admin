package com.pyisland.server.user.mapper;

import com.pyisland.server.user.entity.AgentUsageStats;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 模型用量统计 Mapper。
 */
@Mapper
public interface AgentUsageStatsMapper {

    /**
     * 查询全部模型用量统计。
     */
    List<AgentUsageStats> selectAll();

    /**
     * 按模型名查询。
     */
    AgentUsageStats selectByModelName(@Param("modelName") String modelName);

    /**
     * 增量更新（INSERT ... ON DUPLICATE KEY UPDATE）。
     * delta 值为本次增量。
     */
    int upsertDelta(@Param("modelName") String modelName,
                    @Param("deltaInputTokens") long deltaInputTokens,
                    @Param("deltaCachedTokens") long deltaCachedTokens,
                    @Param("deltaOutputTokens") long deltaOutputTokens,
                    @Param("deltaReasoningTokens") long deltaReasoningTokens,
                    @Param("deltaRequestCount") long deltaRequestCount,
                    @Param("deltaCostMicroFen") long deltaCostMicroFen,
                    @Param("updatedAt") LocalDateTime updatedAt);
}
