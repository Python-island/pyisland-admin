package com.pyisland.server.agent.service;

import org.springframework.stereotype.Service;

/**
 * LangChain 工作流服务（Phase 3 基础版）。
 */
@Service
public class LangChainWorkflowService {

    /**
     * 生成系统提示词。
     *
     * @param proUser 是否 Pro 用户。
     * @return 系统提示词。
     */
    public String buildSystemPrompt(boolean proUser) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是 mihtnelis agent，是eisland软件内置agent，eisland是一个基于windows的灵动岛。你必须遵循 ReAct 工具调用协议。\n")
                .append("可用工具：\n")
                .append("1) user.ip.get: 入参 {}，返回用户公网 IP。\n")
                .append("2) location.by_ip.resolve: 入参 {\"ip\":\"1.2.3.4\"}，返回城市和天气 location。\n")
                .append("3) weather.query: 入参 {\"location\":\"101010100\"}，返回天气与预警。\n");
        if (!proUser) {
            prompt.append("当前用户不是 Pro，禁止调用 weather.query。\n");
        }
        prompt.append("输出规则（必须二选一，且仅输出 JSON，不要 Markdown）：\n")
                .append("A. 调工具时输出：")
                .append("{\"type\":\"tool_call\",\"tool\":\"user.ip.get\",\"arguments\":{}}\n")
                .append("B. 最终回答时输出：")
                .append("{\"type\":\"final\",\"answer\":\"...\"}\n")
                .append("若用户问天气，优先按链路调用：user.ip.get -> location.by_ip.resolve -> weather.query -> final。\n")
                .append("最终回答用中文，简洁准确。");
        return prompt.toString();
    }

    /**
     * 构造用户提示词。
     *
     * @param userPrompt 原始用户输入。
     * @param provider   当前供应商。
     * @return 组合后提示词。
     */
    public String buildUserPrompt(String userPrompt, String provider) {
        String safePrompt = userPrompt == null ? "" : userPrompt.trim();
        String safeProvider = provider == null ? "auto" : provider.trim();
        return "provider=" + safeProvider + "\n\n" + safePrompt;
    }

    public String buildReActUserPrompt(String userPrompt, String provider, String scratchpad) {
        String safePrompt = userPrompt == null ? "" : userPrompt.trim();
        String safeProvider = provider == null ? "auto" : provider.trim();
        String safeScratchpad = scratchpad == null ? "" : scratchpad.trim();
        if (safeScratchpad.isBlank()) {
            return "provider=" + safeProvider + "\n\nUser Question:\n" + safePrompt;
        }
        return "provider=" + safeProvider
                + "\n\nUser Question:\n" + safePrompt
                + "\n\nPrevious Reasoning / Observations:\n" + safeScratchpad
                + "\n\n请基于上面的 Observation 决定下一步：继续 tool_call 或给 final。";
    }
}
