package com.pyisland.server.agent.service;

import com.pyisland.server.agent.config.MihtnelisAgentProperties;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * LangChain4j 网关服务。
 */
@Service
@ConditionalOnProperty(prefix = "mihtnelis.agent.llm", name = "gateway", havingValue = "langchain4j")
public class LangChain4jChatGatewayService implements AgentChatGatewayService {

    private static final Logger log = LoggerFactory.getLogger(LangChain4jChatGatewayService.class);

    private final MihtnelisAgentProperties properties;

    public LangChain4jChatGatewayService(MihtnelisAgentProperties properties) {
        this.properties = properties;
    }

    @Override
    public String chat(String provider, String systemPrompt, String userPrompt) {
        MihtnelisAgentProperties.Provider cfg = resolveProvider();
        if (cfg == null || !cfg.isEnabled()) {
            throw new IllegalStateException("DeepSeek provider is disabled");
        }
        String baseUrl = normalizeBaseUrl(cfg.getBaseUrl());
        String apiKey = normalize(cfg.getApiKey());
        String model = normalize(cfg.getModel());
        if (baseUrl.isBlank()) {
            throw new IllegalStateException("DeepSeek baseUrl is empty");
        }
        if (apiKey.isBlank()) {
            throw new IllegalStateException("DeepSeek apiKey is empty");
        }
        if (model.isBlank()) {
            throw new IllegalStateException("DeepSeek model is empty");
        }
        String prompt = "System:\n" + normalize(systemPrompt) + "\n\nUser:\n" + normalize(userPrompt);
        try {
            OpenAiChatModel modelClient = OpenAiChatModel.builder()
                    .baseUrl(baseUrl)
                    .apiKey(apiKey)
                    .modelName(model)
                    .temperature(0.2)
                    .build();
            String text = normalize(modelClient.generate(prompt));
            if (text.isBlank()) {
                throw new IllegalStateException("DeepSeek returned blank text");
            }
            return text;
        } catch (Exception exception) {
            log.warn("mihtnelis deepseek invoke failed by langchain4j, provider={}, baseUrl={}, model={}, apiKeyPresent={}, reason={}",
                    normalize(provider),
                    baseUrl,
                    model,
                    !apiKey.isBlank(),
                    exception.getMessage());
            throw new IllegalStateException("DeepSeek invoke failed: " + normalize(exception.getMessage()), exception);
        }
    }

    private MihtnelisAgentProperties.Provider resolveProvider() {
        MihtnelisAgentProperties.Llm llm = properties.getLlm();
        if (llm == null) {
            return null;
        }
        return llm.getDeepseek();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeBaseUrl(String value) {
        String base = normalize(value);
        if (base.isBlank()) {
            return "";
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }
}
