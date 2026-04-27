package com.pyisland.server.agent.service;

/**
 * 统一模型网关接口。
 */
public interface AgentChatGatewayService {

    /**
     * 调用模型完成单轮对话。
     *
     * @param provider     供应商。
     * @param systemPrompt 系统提示词。
     * @param userPrompt   用户提示词。
     * @return 模型输出。
     */
    String chat(String provider, String systemPrompt, String userPrompt);

    default boolean supportsNativeToolCalling() {
        return false;
    }

    default String chatWithNativeTools(String provider,
                                       String systemPrompt,
                                       String userPrompt,
                                       AgentToolExecutionService toolExecutionService,
                                       boolean proUser,
                                       AgentToolExecutionService.ExecutionContext context) {
        return chat(provider, systemPrompt, userPrompt);
    }
}
