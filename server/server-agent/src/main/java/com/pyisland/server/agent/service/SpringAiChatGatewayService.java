package com.pyisland.server.agent.service;

import com.pyisland.server.agent.config.MihtnelisAgentProperties;
import com.pyisland.server.agent.utils.AgentStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Service;

/**
 * Spring AI 网关服务。
 */
@Service
@ConditionalOnProperty(prefix = "mihtnelis.agent.llm", name = "gateway", havingValue = "spring-ai", matchIfMissing = true)
public class SpringAiChatGatewayService implements AgentChatGatewayService {

    private static final Logger log = LoggerFactory.getLogger(SpringAiChatGatewayService.class);

    private final MihtnelisAgentProperties properties;

    public SpringAiChatGatewayService(MihtnelisAgentProperties properties) {
        this.properties = properties;
    }

    /**
     * 调用模型完成单轮对话。
     *
     * @param provider     供应商。
     * @param systemPrompt 系统提示词。
     * @param userPrompt   用户提示词。
     * @return 模型输出文本；无法调用时返回 null。
     */
    @Override
    public String chat(String provider, String systemPrompt, String userPrompt) {
        MihtnelisAgentProperties.Provider cfg = resolveProvider();
        if (cfg == null || !cfg.isEnabled()) {
            throw new IllegalStateException("DeepSeek provider is disabled");
        }
        String baseUrl = AgentStringUtils.trimTrailingSlash(cfg.getBaseUrl());
        String apiKey = AgentStringUtils.trimToEmpty(cfg.getApiKey());
        String model = AgentStringUtils.trimToEmpty(cfg.getModel());
        if (baseUrl.isBlank()) {
            throw new IllegalStateException("DeepSeek baseUrl is empty");
        }
        if (apiKey.isBlank()) {
            throw new IllegalStateException("DeepSeek apiKey is empty");
        }
        if (model.isBlank()) {
            throw new IllegalStateException("DeepSeek model is empty");
        }

        try {
            String safeSystemPrompt = AgentStringUtils.trimToEmpty(systemPrompt);
            String safeUserPrompt = AgentStringUtils.trimToEmpty(userPrompt);
            OpenAiApi openAiApi = OpenAiApi.builder()
                    .baseUrl(baseUrl)
                    .apiKey(apiKey)
                    .build();
            OpenAiChatModel chatModel = OpenAiChatModel.builder()
                    .openAiApi(openAiApi)
                    .defaultOptions(OpenAiChatOptions.builder()
                            .model(model)
                            .temperature(0.2)
                            .build())
                    .build();
            Prompt prompt = new Prompt(
                    new SystemMessage(safeSystemPrompt),
                    new UserMessage(safeUserPrompt)
            );
            ChatResponse response = chatModel.call(prompt);
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                throw new IllegalStateException("DeepSeek returned empty response");
            }
            String text = AgentStringUtils.trimToEmpty(response.getResult().getOutput().getText());
            if (text.isBlank()) {
                throw new IllegalStateException("DeepSeek returned blank text");
            }
            return text;
        } catch (Exception exception) {
            log.warn("mihtnelis deepseek invoke failed, provider={}, baseUrl={}, model={}, apiKeyPresent={}, reason={}",
                    AgentStringUtils.trimToEmpty(provider),
                    baseUrl,
                    model,
                    !apiKey.isBlank(),
                    exception.getMessage());
            throw new IllegalStateException("DeepSeek invoke failed: " + AgentStringUtils.trimToEmpty(exception.getMessage()), exception);
        }
    }

    private MihtnelisAgentProperties.Provider resolveProvider() {
        MihtnelisAgentProperties.Llm llm = properties.getLlm();
        if (llm == null) {
            return null;
        }
        return llm.getDeepseek();
    }
}
