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
import java.util.List;
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
    public String chat(String provider,
                       String systemPrompt,
                       String userPrompt,
                       ChatRequestOptions requestOptions) {
        OpenAiChatModel modelClient = buildModelClient();
        ChatRequestOptions safeRequestOptions = normalizeRequestOptions(requestOptions);
        String prompt = "System:\n"
                + AgentStringUtils.trimToEmpty(systemPrompt)
                + "\n\nUser:\n"
                + appendThinkingHint(userPrompt, safeRequestOptions);
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
                                      AgentToolExecutionService.ExecutionContext context,
                                      ChatRequestOptions requestOptions) {
        OpenAiChatModel modelClient = buildModelClient();
        ChatRequestOptions safeRequestOptions = normalizeRequestOptions(requestOptions);
        NativeToolBridge toolBridge = new NativeToolBridge(toolExecutionService, proUser, context);
        NativeToolAssistant assistant = AiServices.builder(NativeToolAssistant.class)
                .chatLanguageModel(modelClient)
                .tools(toolBridge)
                .build();
        try {
            String result = AgentStringUtils.trimToEmpty(assistant.chat(
                    AgentStringUtils.trimToEmpty(systemPrompt),
                    appendThinkingHint(userPrompt, safeRequestOptions)
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

    private ChatRequestOptions normalizeRequestOptions(ChatRequestOptions requestOptions) {
        if (requestOptions == null) {
            return new ChatRequestOptions(false, "medium");
        }
        String effort = AgentStringUtils.trimToEmpty(requestOptions.reasoningEffort()).toLowerCase();
        if (!"low".equals(effort) && !"high".equals(effort)) {
            effort = "medium";
        }
        return new ChatRequestOptions(requestOptions.thinkingEnabled(), effort);
    }

    private String appendThinkingHint(String userPrompt, ChatRequestOptions requestOptions) {
        String safePrompt = AgentStringUtils.trimToEmpty(userPrompt);
        String effort = requestOptions == null ? "medium" : requestOptions.reasoningEffort();
        boolean thinkingEnabled = requestOptions != null && requestOptions.thinkingEnabled();
        return safePrompt
                + "\n\n[deepseek_options] thinking=" + thinkingEnabled
                + ", reasoning_effort=" + AgentStringUtils.trimToDefault(effort, "medium");
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

        @Tool("根据城市名查找天气 location")
        public Map<String, Object> weatherCityLookup(@P("query") String query,
                                                     @P("lang") String lang) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (query != null && !query.isBlank()) {
                arguments.put("query", AgentStringUtils.trimToEmpty(query));
            }
            if (lang != null && !lang.isBlank()) {
                arguments.put("lang", AgentStringUtils.trimToEmpty(lang));
            }
            return invoke("weather.city.lookup", arguments);
        }

        @Tool("根据城市名直接查询天气与预警")
        public Map<String, Object> weatherByCityQuery(@P("query") String query,
                                                      @P("lang") String lang) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (query != null && !query.isBlank()) {
                arguments.put("query", AgentStringUtils.trimToEmpty(query));
            }
            if (lang != null && !lang.isBlank()) {
                arguments.put("lang", AgentStringUtils.trimToEmpty(lang));
            }
            return invoke("weather.by_city.query", arguments);
        }

        @Tool("查询天气接口月配额状态")
        public Map<String, Object> weatherQuotaStatus() {
            return invoke("weather.quota.status", Map.of());
        }

        @Tool("获取当前系统时间和时区")
        public Map<String, Object> timeNow() {
            return invoke("time.now", Map.of());
        }

        @Tool("获取当前会话上下文")
        public Map<String, Object> sessionContextGet() {
            return invoke("session.context.get", Map.of());
        }

        @Tool("联网搜索网页结果")
        public Map<String, Object> webSearch(@P("query") String query,
                                             @P("limit") Integer limit) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (query != null && !query.isBlank()) {
                arguments.put("query", AgentStringUtils.trimToEmpty(query));
            }
            if (limit != null) {
                arguments.put("limit", Math.max(1, Math.min(10, limit)));
            }
            return invoke("web.search", arguments);
        }

        @Tool("读取指定网页正文，需用户授权")
        public Map<String, Object> webPageRead(@P("url") String url) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (url != null && !url.isBlank()) {
                arguments.put("url", AgentStringUtils.trimToEmpty(url));
            }
            return invoke("web.page.read", arguments);
        }

        @Tool("列出本地目录内容，客户端执行")
        public Map<String, Object> fileList(@P("path") String path,
                                            @P("limit") Integer limit) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (path != null && !path.isBlank()) {
                arguments.put("path", AgentStringUtils.trimToEmpty(path));
            }
            if (limit != null) {
                arguments.put("limit", Math.max(1, Math.min(500, limit)));
            }
            return invoke("file.list", arguments);
        }

        @Tool("读取本地文本文件，客户端执行")
        public Map<String, Object> fileRead(@P("path") String path) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (path != null && !path.isBlank()) {
                arguments.put("path", AgentStringUtils.trimToEmpty(path));
            }
            return invoke("file.read", arguments);
        }

        @Tool("写入本地文本文件，客户端执行")
        public Map<String, Object> fileWrite(@P("path") String path,
                                             @P("content") String content) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (path != null && !path.isBlank()) {
                arguments.put("path", AgentStringUtils.trimToEmpty(path));
            }
            arguments.put("content", content == null ? "" : content);
            return invoke("file.write", arguments);
        }

        @Tool("删除本地文件或目录，客户端执行")
        public Map<String, Object> fileDelete(@P("path") String path) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (path != null && !path.isBlank()) {
                arguments.put("path", AgentStringUtils.trimToEmpty(path));
            }
            return invoke("file.delete", arguments);
        }

        @Tool("执行本地命令行，客户端执行")
        public Map<String, Object> cmdExec(@P("command") String command,
                                           @P("cwd") String cwd,
                                           @P("timeoutMs") Integer timeoutMs) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (command != null && !command.isBlank()) {
                arguments.put("command", AgentStringUtils.trimToEmpty(command));
            }
            if (cwd != null && !cwd.isBlank()) {
                arguments.put("cwd", AgentStringUtils.trimToEmpty(cwd));
            }
            if (timeoutMs != null) {
                arguments.put("timeoutMs", Math.max(1000, Math.min(60000, timeoutMs)));
            }
            return invoke("cmd.exec", arguments);
        }

        @Tool("在本地文件内容中搜索匹配文本，客户端执行")
        public Map<String, Object> fileGrep(@P("path") String path,
                                            @P("pattern") String pattern,
                                            @P("limit") Integer limit) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (path != null && !path.isBlank()) {
                arguments.put("path", AgentStringUtils.trimToEmpty(path));
            }
            if (pattern != null && !pattern.isBlank()) {
                arguments.put("pattern", AgentStringUtils.trimToEmpty(pattern));
            }
            if (limit != null) {
                arguments.put("limit", Math.max(1, Math.min(200, limit)));
            }
            return invoke("file.grep", arguments);
        }

        @Tool("按文件名搜索本地文件，客户端执行")
        public Map<String, Object> fileSearch(@P("path") String path,
                                              @P("keyword") String keyword,
                                              @P("limit") Integer limit) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (path != null && !path.isBlank()) {
                arguments.put("path", AgentStringUtils.trimToEmpty(path));
            }
            if (keyword != null && !keyword.isBlank()) {
                arguments.put("keyword", AgentStringUtils.trimToEmpty(keyword));
            }
            if (limit != null) {
                arguments.put("limit", Math.max(1, Math.min(200, limit)));
            }
            return invoke("file.search", arguments);
        }

        @Tool("更新任务清单快照")
        public Map<String, Object> agentTodoWrite(@P("items") Object items) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            arguments.put("items", items == null ? List.of() : items);
            return invoke("agent.todo.write", arguments);
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
