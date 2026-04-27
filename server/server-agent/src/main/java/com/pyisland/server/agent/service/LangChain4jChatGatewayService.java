package com.pyisland.server.agent.service;

import com.pyisland.server.agent.config.MihtnelisAgentProperties;
import com.pyisland.server.agent.utils.AgentStringUtils;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

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
        OpenAiChatModel modelClient = buildModelClient();
        String prompt = "System:\n" + AgentStringUtils.trimToEmpty(systemPrompt) + "\n\nUser:\n" + AgentStringUtils.trimToEmpty(userPrompt);
        try {
            String text = AgentStringUtils.trimToEmpty(modelClient.generate(prompt));
            if (text.isBlank()) {
                throw new IllegalStateException("DeepSeek returned blank text");
            }
            return text;
        } catch (Exception exception) {
            MihtnelisAgentProperties.Provider cfg = resolveProvider();
            log.warn("mihtnelis deepseek invoke failed by langchain4j, provider={}, baseUrl={}, model={}, apiKeyPresent={}, reason={}",
                    AgentStringUtils.trimToEmpty(provider),
                    AgentStringUtils.trimTrailingSlash(cfg == null ? "" : cfg.getBaseUrl()),
                    AgentStringUtils.trimToEmpty(cfg == null ? "" : cfg.getModel()),
                    !AgentStringUtils.trimToEmpty(cfg == null ? "" : cfg.getApiKey()).isBlank(),
                    exception.getMessage());
            throw new IllegalStateException("DeepSeek invoke failed: " + AgentStringUtils.trimToEmpty(exception.getMessage()), exception);
        }
    }

    @Override
    public boolean supportsNativeToolCalling() {
        return true;
    }

    @Override
    public String chatWithNativeTools(String provider,
                                      String systemPrompt,
                                      String userPrompt,
                                      AgentToolExecutionService toolExecutionService,
                                      boolean proUser,
                                      AgentToolExecutionService.ExecutionContext context) {
        OpenAiChatModel modelClient = buildModelClient();
        NativeToolBridge toolBridge = new NativeToolBridge(toolExecutionService, proUser, context);
        NativeToolAssistant assistant = AiServices.builder(NativeToolAssistant.class)
                .chatLanguageModel(modelClient)
                .tools(toolBridge)
                .build();
        try {
            String result = AgentStringUtils.trimToEmpty(assistant.chat(
                    AgentStringUtils.trimToEmpty(systemPrompt),
                    AgentStringUtils.trimToEmpty(userPrompt)
            ));
            if (result.isBlank()) {
                throw new IllegalStateException("DeepSeek returned blank text");
            }
            return result;
        } catch (Exception exception) {
            MihtnelisAgentProperties.Provider cfg = resolveProvider();
            log.warn("mihtnelis native tool invoke failed by langchain4j, provider={}, baseUrl={}, model={}, apiKeyPresent={}, reason={}",
                    AgentStringUtils.trimToEmpty(provider),
                    AgentStringUtils.trimTrailingSlash(cfg == null ? "" : cfg.getBaseUrl()),
                    AgentStringUtils.trimToEmpty(cfg == null ? "" : cfg.getModel()),
                    !AgentStringUtils.trimToEmpty(cfg == null ? "" : cfg.getApiKey()).isBlank(),
                    exception.getMessage());
            throw new IllegalStateException("DeepSeek invoke failed: " + AgentStringUtils.trimToEmpty(exception.getMessage()), exception);
        }
    }

    private OpenAiChatModel buildModelClient() {
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
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(model)
                .temperature(0.2)
                .build();
    }

    private MihtnelisAgentProperties.Provider resolveProvider() {
        MihtnelisAgentProperties.Llm llm = properties.getLlm();
        if (llm == null) {
            return null;
        }
        return llm.getDeepseek();
    }

    private interface NativeToolAssistant {

        @SystemMessage("{{systemPrompt}}")
        @UserMessage("{{userPrompt}}")
        String chat(@V("systemPrompt") String systemPrompt,
                    @V("userPrompt") String userPrompt);
    }

    private static class NativeToolBridge {

        private final AgentToolExecutionService toolExecutionService;
        private final boolean proUser;
        private final AgentToolExecutionService.ExecutionContext context;

        private NativeToolBridge(AgentToolExecutionService toolExecutionService,
                                 boolean proUser,
                                 AgentToolExecutionService.ExecutionContext context) {
            this.toolExecutionService = toolExecutionService;
            this.proUser = proUser;
            this.context = context;
        }

        @Tool("获取当前用户公网IP")
        public Map<String, Object> userIpGet() {
            return invoke("user.ip.get", Map.of());
        }

        @Tool("根据IP解析用户所在城市与天气location")
        public Map<String, Object> locationByIpResolve(@P("ip") String ip) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (ip != null && !ip.isBlank()) {
                arguments.put("ip", AgentStringUtils.trimToEmpty(ip));
            }
            return invoke("location.by_ip.resolve", arguments);
        }

        @Tool("根据location查询天气与预警")
        public Map<String, Object> weatherQuery(@P("location") String location) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (location != null && !location.isBlank()) {
                arguments.put("location", AgentStringUtils.trimToEmpty(location));
            }
            return invoke("weather.query", arguments);
        }

        private Map<String, Object> invoke(String toolName, Map<String, Object> arguments) {
            AgentToolExecutionService.ToolResult result = toolExecutionService.execute(toolName, arguments, proUser, context);
            return Map.of(
                    "tool", result.tool(),
                    "success", result.success(),
                    "data", result.data(),
                    "error", result.error()
            );
        }
    }
}
