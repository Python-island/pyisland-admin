package com.pyisland.server.agent.mq;

/**
 * Agent 计费扣减异步落库消息。
 *
 * @param username  用户名。
 * @param amountFen 扣减金额（分，字符串形式保留 8 位小数）。
 * @param modelName 模型名。
 * @param inputTokens 输入 token 数。
 * @param outputTokens 输出 token 数。
 * @param lastError 上次错误信息（重试时携带）。
 */
public record AgentBillingDeductMessage(
        String username,
        String amountFen,
        String modelName,
        int inputTokens,
        int outputTokens,
        String lastError
) {
    public AgentBillingDeductMessage(String username, String amountFen, String modelName,
                                     int inputTokens, int outputTokens) {
        this(username, amountFen, modelName, inputTokens, outputTokens, null);
    }
}
