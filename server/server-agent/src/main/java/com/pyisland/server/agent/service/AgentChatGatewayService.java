package com.pyisland.server.agent.service;

/**
 * 统一模型网关接口。
 */
public interface AgentChatGatewayService {

    record ChatRequestOptions(boolean thinkingEnabled, String reasoningEffort, String model) {
    }

    /**
     * 实时推理内容监听器，用于流式输出 reasoning_content 块。
     */
    @FunctionalInterface
    interface ReasoningStreamListener {
        /**
         * 收到一段推理内容增量。
         *
         * @param deltaText 增量文本。
         * @param done      是否为最后一段。
         */
        void onReasoningDelta(String deltaText, boolean done);
    }

    /**
     * 调用模型完成单轮对话。
     *
     * @param provider     供应商。
     * @param systemPrompt 系统提示词。
     * @param userPrompt   用户提示词。
     * @return 模型输出。
     */
    String chat(String provider, String systemPrompt, String userPrompt, ChatRequestOptions requestOptions);

    /**
     * 调用模型完成单轮对话，支持实时推理内容回调。
     */
    default String chat(String provider, String systemPrompt, String userPrompt,
                        ChatRequestOptions requestOptions, ReasoningStreamListener reasoningListener) {
        return chat(provider, systemPrompt, userPrompt, requestOptions);
    }

    default String chat(String provider, String systemPrompt, String userPrompt) {
        return chat(provider, systemPrompt, userPrompt, new ChatRequestOptions(false, "medium", null));
    }

    default boolean supportsNativeToolCalling() {
        return false;
    }

    default String chatWithNativeTools(String provider,
                                       String systemPrompt,
                                       String userPrompt,
                                       AgentToolExecutionService toolExecutionService,
                                       boolean proUser,
                                       AgentToolExecutionService.ExecutionContext context,
                                       ChatRequestOptions requestOptions) {
        return chat(provider, systemPrompt, userPrompt, requestOptions);
    }

    default String chatWithNativeTools(String provider,
                                       String systemPrompt,
                                       String userPrompt,
                                       AgentToolExecutionService toolExecutionService,
                                       boolean proUser,
                                       AgentToolExecutionService.ExecutionContext context) {
        return chatWithNativeTools(
                provider,
                systemPrompt,
                userPrompt,
                toolExecutionService,
                proUser,
                context,
                new ChatRequestOptions(false, "medium", null)
        );
    }
}
