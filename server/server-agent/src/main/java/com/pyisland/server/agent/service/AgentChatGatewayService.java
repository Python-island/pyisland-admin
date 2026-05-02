package com.pyisland.server.agent.service;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 统一模型网关接口。
 */
public interface AgentChatGatewayService {

    record ChatRequestOptions(boolean thinkingEnabled, String reasoningEffort, String model) {
    }

    /**
     * 流式内容监听器，实时接收 reasoning_content 和 content 增量。
     */
    interface ChatStreamListener {
        /** 收到一段推理内容增量。 */
        default void onReasoningDelta(String deltaText, boolean done) {}
        /** 收到一段正文内容增量。 */
        default void onContentDelta(String deltaText, boolean done) {}
    }

    /**
     * 累积 LLM API 返回的真实 token 用量（支持多轮 ReAct 迭代累加）。
     */
    class TokenUsageAccumulator {
        private final AtomicInteger promptTokens = new AtomicInteger(0);
        private final AtomicInteger completionTokens = new AtomicInteger(0);
        private final AtomicInteger reasoningTokens = new AtomicInteger(0);
        private final AtomicInteger cachedTokens = new AtomicInteger(0);

        /** 将一次 API 调用返回的 usage 累加进来。 */
        public void add(int prompt, int completion, int reasoning) {
            add(prompt, completion, reasoning, 0);
        }

        public void add(int prompt, int completion, int reasoning, int cached) {
            promptTokens.addAndGet(prompt);
            completionTokens.addAndGet(completion);
            reasoningTokens.addAndGet(reasoning);
            cachedTokens.addAndGet(cached);
        }

        public void add(int prompt, int completion) {
            add(prompt, completion, 0, 0);
        }

        public int getPromptTokens() { return promptTokens.get(); }
        public int getCompletionTokens() { return completionTokens.get(); }
        public int getReasoningTokens() { return reasoningTokens.get(); }
        public int getCachedTokens() { return cachedTokens.get(); }
        public int getTotalTokens() { return promptTokens.get() + completionTokens.get(); }
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
                        ChatRequestOptions requestOptions, ChatStreamListener streamListener) {
        return chat(provider, systemPrompt, userPrompt, requestOptions);
    }

    /**
     * 调用模型完成单轮对话，支持实时推理/正文回调 + token 用量累积。
     */
    default String chat(String provider, String systemPrompt, String userPrompt,
                        ChatRequestOptions requestOptions, ChatStreamListener streamListener,
                        TokenUsageAccumulator usageAccumulator) {
        return chat(provider, systemPrompt, userPrompt, requestOptions, streamListener);
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
