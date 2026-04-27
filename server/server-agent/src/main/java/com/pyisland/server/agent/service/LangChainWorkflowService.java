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
        if (proUser) {
            return "你是 mihtnelis agent。优先给出可执行步骤，并在合适时建议可用工具能力（天气、todo、本地文件、音乐控制、向量检索）。";
        }
        return "你是 mihtnelis agent。回答要准确简洁，仅使用普通用户可用能力。";
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
}
