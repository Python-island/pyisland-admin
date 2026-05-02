package com.pyisland.server.agent.mq;

/**
 * Agent 用量统计异步落库消息。
 *
 * @param modelName       模型名。
 * @param inputTokens     输入 token 数。
 * @param cachedTokens    缓存命中 token 数。
 * @param outputTokens    输出 token 数。
 * @param reasoningTokens 推理 token 数。
 * @param costMicroFen    费用（微分，1 微分 = 0.00000001 分）。
 */
public record AgentUsageStatsMessage(
        String modelName,
        int inputTokens,
        int cachedTokens,
        int outputTokens,
        int reasoningTokens,
        long costMicroFen
) {
}
