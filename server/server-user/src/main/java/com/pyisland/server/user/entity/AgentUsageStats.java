package com.pyisland.server.user.entity;

import java.time.LocalDateTime;

/**
 * 全体用户 Agent 模型用量统计实体。
 * 按模型名聚合所有用户的 token 用量和费用。
 */
public class AgentUsageStats {

    private Long id;
    private String modelName;
    private Long totalInputTokens;
    private Long totalCachedTokens;
    private Long totalOutputTokens;
    private Long totalReasoningTokens;
    private Long totalRequestCount;
    /** 总费用（分，8 位小数存为 long 微分，1 微分 = 0.00000001 分） */
    private Long totalCostMicroFen;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public Long getTotalInputTokens() { return totalInputTokens; }
    public void setTotalInputTokens(Long totalInputTokens) { this.totalInputTokens = totalInputTokens; }

    public Long getTotalCachedTokens() { return totalCachedTokens; }
    public void setTotalCachedTokens(Long totalCachedTokens) { this.totalCachedTokens = totalCachedTokens; }

    public Long getTotalOutputTokens() { return totalOutputTokens; }
    public void setTotalOutputTokens(Long totalOutputTokens) { this.totalOutputTokens = totalOutputTokens; }

    public Long getTotalReasoningTokens() { return totalReasoningTokens; }
    public void setTotalReasoningTokens(Long totalReasoningTokens) { this.totalReasoningTokens = totalReasoningTokens; }

    public Long getTotalRequestCount() { return totalRequestCount; }
    public void setTotalRequestCount(Long totalRequestCount) { this.totalRequestCount = totalRequestCount; }

    public Long getTotalCostMicroFen() { return totalCostMicroFen; }
    public void setTotalCostMicroFen(Long totalCostMicroFen) { this.totalCostMicroFen = totalCostMicroFen; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
