package com.pyisland.server.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final MihtnelisAgentProperties properties;

    public LangChain4jChatGatewayService(MihtnelisAgentProperties properties) {
        this.properties = properties;
    }

    @Override
    public String chat(String provider,
                       String systemPrompt,
                       String userPrompt,
                       ChatRequestOptions requestOptions) {
        return chat(provider, systemPrompt, userPrompt, requestOptions, null, null);
    }

    @Override
    public String chat(String provider,
                       String systemPrompt,
                       String userPrompt,
                       ChatRequestOptions requestOptions,
                       ChatStreamListener streamListener) {
        return chat(provider, systemPrompt, userPrompt, requestOptions, streamListener, null);
    }

    @Override
    public String chat(String provider,
                       String systemPrompt,
                       String userPrompt,
                       ChatRequestOptions requestOptions,
                       ChatStreamListener streamListener,
                       TokenUsageAccumulator usageAccumulator) {
        ChatRequestOptions safeRequestOptions = normalizeRequestOptions(requestOptions);
        // thinking 开启 或 streamListener 非空时，走原生 HTTP 调用（支持流式推送）
        if (safeRequestOptions.thinkingEnabled() || streamListener != null) {
            return chatWithThinkingHttp(provider, systemPrompt, userPrompt, safeRequestOptions, streamListener, usageAccumulator);
        }
        OpenAiChatModel modelClient = buildModelClient(provider, requestOptions);
        String prompt = "System:\n"
                + AgentStringUtils.trimToEmpty(systemPrompt)
                + "\n\nUser:\n"
                + AgentStringUtils.trimToEmpty(userPrompt);
        try {
            String text = AgentStringUtils.trimToEmpty(modelClient.generate(prompt));
            if (text.isBlank()) {
                throw new IllegalStateException("LLM returned blank text");
            }
            return text;
        } catch (Exception exception) {
            MihtnelisAgentProperties.Provider cfg = resolveProvider(provider);
            log.warn("mihtnelis LLM invoke failed by langchain4j, provider={}, baseUrl={}, model={}, apiKeyPresent={}, reason={}",
                    AgentStringUtils.trimToEmpty(provider),
                    AgentStringUtils.trimTrailingSlash(cfg == null ? "" : cfg.getBaseUrl()),
                    AgentStringUtils.trimToEmpty(cfg == null ? "" : cfg.getModel()),
                    !AgentStringUtils.trimToEmpty(cfg == null ? "" : cfg.getApiKey()).isBlank(),
                    exception.getMessage());
            throw new IllegalStateException("LLM invoke failed (" + AgentStringUtils.trimToEmpty(provider) + "): " + AgentStringUtils.trimToEmpty(exception.getMessage()), exception);
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
        OpenAiChatModel modelClient = buildModelClient(provider, requestOptions);
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
                throw new IllegalStateException("LLM returned blank text");
            }
            return result;
        } catch (Exception exception) {
            MihtnelisAgentProperties.Provider cfg = resolveProvider(provider);
            log.warn("mihtnelis native tool invoke failed by langchain4j, provider={}, baseUrl={}, model={}, apiKeyPresent={}, reason={}",
                    AgentStringUtils.trimToEmpty(provider),
                    AgentStringUtils.trimTrailingSlash(cfg == null ? "" : cfg.getBaseUrl()),
                    AgentStringUtils.trimToEmpty(cfg == null ? "" : cfg.getModel()),
                    !AgentStringUtils.trimToEmpty(cfg == null ? "" : cfg.getApiKey()).isBlank(),
                    exception.getMessage());
            throw new IllegalStateException("LLM invoke failed (" + AgentStringUtils.trimToEmpty(provider) + "): " + AgentStringUtils.trimToEmpty(exception.getMessage()), exception);
        }
    }

    private OpenAiChatModel buildModelClient(String provider, ChatRequestOptions requestOptions) {
        MihtnelisAgentProperties.Provider cfg = resolveProvider(provider);
        if (cfg == null || !cfg.isEnabled()) {
            throw new IllegalStateException("LLM provider is disabled: " + AgentStringUtils.trimToEmpty(provider));
        }
        String baseUrl = AgentStringUtils.trimTrailingSlash(cfg.getBaseUrl());
        String apiKey = AgentStringUtils.trimToEmpty(cfg.getApiKey());
        String clientModel = requestOptions == null ? "" : AgentStringUtils.trimToEmpty(requestOptions.model());
        String model = clientModel.isBlank() ? AgentStringUtils.trimToEmpty(cfg.getModel()) : clientModel;
        if (baseUrl.isBlank()) {
            throw new IllegalStateException("LLM provider baseUrl is empty: " + AgentStringUtils.trimToEmpty(provider));
        }
        if (apiKey.isBlank()) {
            throw new IllegalStateException("LLM provider apiKey is empty: " + AgentStringUtils.trimToEmpty(provider));
        }
        if (model.isBlank()) {
            throw new IllegalStateException("LLM provider model is empty: " + AgentStringUtils.trimToEmpty(provider));
        }
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(model)
                .temperature(0.2)
                .build();
    }

    private MihtnelisAgentProperties.Provider resolveProvider(String provider) {
        MihtnelisAgentProperties.Llm llm = properties.getLlm();
        if (llm == null) {
            return null;
        }
        if ("mimo".equalsIgnoreCase(AgentStringUtils.trimToEmpty(provider))) {
            return llm.getMimo();
        }
        return llm.getDeepseek();
    }

    private ChatRequestOptions normalizeRequestOptions(ChatRequestOptions requestOptions) {
        if (requestOptions == null) {
            return new ChatRequestOptions(false, "medium", null);
        }
        String effort = AgentStringUtils.trimToEmpty(requestOptions.reasoningEffort()).toLowerCase();
        if (!"low".equals(effort) && !"high".equals(effort)) {
            effort = "medium";
        }
        return new ChatRequestOptions(requestOptions.thinkingEnabled(), effort, requestOptions.model());
    }

    /**
     * thinking 开启时，绕过 LangChain4j，直接用原生 HTTP 调用 DeepSeek API，
     * 以正确设置 thinking / reasoning_effort 参数并解析 reasoning_content。
     * <p>
     * 当提供 {@code reasoningListener} 时，使用流式模式（stream=true），
     * 实时推送 reasoning_content 增量；否则退回非流式模式。
     */
    private String chatWithThinkingHttp(String provider,
                                        String systemPrompt,
                                        String userPrompt,
                                        ChatRequestOptions requestOptions,
                                        ChatStreamListener streamListener,
                                        TokenUsageAccumulator usageAccumulator) {
        MihtnelisAgentProperties.Provider cfg = resolveProvider(provider);
        if (cfg == null || !cfg.isEnabled()) {
            throw new IllegalStateException("LLM provider is disabled: " + AgentStringUtils.trimToEmpty(provider));
        }
        String baseUrl = AgentStringUtils.trimTrailingSlash(cfg.getBaseUrl());
        String apiKey = AgentStringUtils.trimToEmpty(cfg.getApiKey());
        String clientModel = requestOptions == null ? "" : AgentStringUtils.trimToEmpty(requestOptions.model());
        String model = clientModel.isBlank() ? AgentStringUtils.trimToEmpty(cfg.getModel()) : clientModel;
        String effort = requestOptions == null
                ? "medium"
                : AgentStringUtils.trimToDefault(requestOptions.reasoningEffort(), "medium");
        boolean useStream = streamListener != null;
        try {
            String url = resolveCompletionsUrl(baseUrl);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            payload.put("messages", new Object[]{
                    Map.of("role", "system", "content", AgentStringUtils.trimToEmpty(systemPrompt)),
                    Map.of("role", "user", "content", AgentStringUtils.trimToEmpty(userPrompt))
            });
            payload.put("temperature", 0.2);
            payload.put("stream", useStream);
            if (useStream) {
                payload.put("stream_options", Map.of("include_usage", true));
            }
            if (requestOptions != null && requestOptions.thinkingEnabled()) {
                payload.put("thinking", Map.of("type", "enabled"));
                payload.put("reasoning_effort", effort);
            }
            String requestBody = OBJECT_MAPPER.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            if (useStream) {
                return chatWithThinkingStream(request, streamListener, usageAccumulator);
            }
            return chatWithThinkingBlocking(request, usageAccumulator);
        } catch (Exception exception) {
            log.warn("mihtnelis LLM thinking-http invoke failed, provider={}, baseUrl={}, model={}, stream={}, reason={}",
                    AgentStringUtils.trimToEmpty(provider), baseUrl, model, useStream, exception.getMessage());
            throw new IllegalStateException("LLM invoke failed (" + AgentStringUtils.trimToEmpty(provider) + "): " + AgentStringUtils.trimToEmpty(exception.getMessage()), exception);
        }
    }

    /**
     * 非流式 thinking HTTP 调用（兼容旧逻辑）。
     */
    private String chatWithThinkingBlocking(HttpRequest request, TokenUsageAccumulator usageAccumulator) throws Exception {
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("LLM HTTP " + response.statusCode() + ": " + AgentStringUtils.trimToEmpty(response.body()));
        }
        JsonNode root = OBJECT_MAPPER.readTree(response.body());
        // 提取真实 token 用量
        populateUsageFromResponse(root, usageAccumulator);
        JsonNode messageNode = root.path("choices").isArray() && !root.path("choices").isEmpty()
                ? root.path("choices").get(0).path("message")
                : OBJECT_MAPPER.createObjectNode();
        String content = extractNodeText(messageNode.path("content"));
        String reasoningContent = extractNodeText(messageNode.path("reasoning_content"));
        if (content.isBlank() && !reasoningContent.isBlank()) {
            content = extractNodeText(root.path("content"));
        }
        if (content.isBlank() && reasoningContent.isBlank()) {
            throw new IllegalStateException("LLM returned blank text");
        }
        if (!reasoningContent.isBlank()) {
            return "<think>" + reasoningContent + "</think>\n" + content;
        }
        return content;
    }

    /**
     * 流式 thinking HTTP 调用，实时推送 reasoning_content 到监听器。
     * SSE 格式：每行 "data: {...}" 包含 delta.reasoning_content 和 delta.content。
     */
    private String chatWithThinkingStream(HttpRequest request,
                                           ChatStreamListener streamListener,
                                           TokenUsageAccumulator usageAccumulator) throws Exception {
        HttpResponse<java.io.InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String errorBody;
            try (java.io.InputStream is = response.body()) {
                errorBody = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
            throw new IllegalStateException("LLM HTTP " + response.statusCode() + ": " + AgentStringUtils.trimToEmpty(errorBody));
        }
        StringBuilder reasoningAccum = new StringBuilder();
        StringBuilder contentAccum = new StringBuilder();
        boolean reasoningDone = false;
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if ("[DONE]".equals(data)) {
                    break;
                }
                try {
                    JsonNode chunk = OBJECT_MAPPER.readTree(data);
                    // 流式最后一个 chunk 包含 usage（需 stream_options.include_usage=true）
                    populateUsageFromResponse(chunk, usageAccumulator);
                    JsonNode delta = chunk.path("choices").isArray() && !chunk.path("choices").isEmpty()
                            ? chunk.path("choices").get(0).path("delta")
                            : OBJECT_MAPPER.createObjectNode();
                    // reasoning_content 增量
                    String reasoningDelta = delta.path("reasoning_content").asText("");
                    if (!reasoningDelta.isEmpty()) {
                        reasoningAccum.append(reasoningDelta);
                        streamListener.onReasoningDelta(reasoningDelta, false);
                    }
                    // content 增量（reasoning 结束后开始出现）
                    String contentDelta = delta.path("content").asText("");
                    if (!contentDelta.isEmpty()) {
                        if (!reasoningDone && reasoningAccum.length() > 0) {
                            reasoningDone = true;
                            streamListener.onReasoningDelta("", true);
                        }
                        contentAccum.append(contentDelta);
                        streamListener.onContentDelta(contentDelta, false);
                    }
                } catch (Exception parseEx) {
                    log.debug("mihtnelis stream: failed to parse SSE chunk: {}", data);
                }
            }
        }
        // 如果从未收到 content 但 reasoning 已结束
        if (!reasoningDone && reasoningAccum.length() > 0) {
            streamListener.onReasoningDelta("", true);
        }
        // 通知 content 流结束
        if (contentAccum.length() > 0) {
            streamListener.onContentDelta("", true);
        }
        String reasoning = reasoningAccum.toString().trim();
        String content = contentAccum.toString().trim();
        if (content.isBlank() && reasoning.isBlank()) {
            throw new IllegalStateException("LLM returned blank text");
        }
        if (!reasoning.isBlank()) {
            return "<think>" + reasoning + "</think>\n" + content;
        }
        return content;
    }

    /**
     * 从 API 响应 JSON 中提取 usage 字段并累加到 accumulator。
     * 非流式响应和流式最后一个 chunk 都包含 usage 节点。
     */
    private void populateUsageFromResponse(JsonNode root, TokenUsageAccumulator usageAccumulator) {
        if (usageAccumulator == null || root == null) {
            return;
        }
        JsonNode usage = root.path("usage");
        if (usage.isMissingNode() || usage.isNull()) {
            return;
        }
        int prompt = usage.path("prompt_tokens").asInt(0);
        int completion = usage.path("completion_tokens").asInt(0);
        // 部分供应商在 completion_tokens_details 中提供 reasoning_tokens
        int reasoning = usage.path("completion_tokens_details").path("reasoning_tokens").asInt(0);
        // MiMo 等供应商在 prompt_tokens_details 中提供 cached_tokens（缓存命中 token 数）
        int cached = usage.path("prompt_tokens_details").path("cached_tokens").asInt(0);
        if (prompt > 0 || completion > 0) {
            usageAccumulator.add(prompt, completion, reasoning, cached);
        }
    }

    private String resolveCompletionsUrl(String baseUrl) {
        String safeBaseUrl = AgentStringUtils.trimTrailingSlash(baseUrl);
        if (safeBaseUrl.endsWith("/chat/completions") || safeBaseUrl.endsWith("/v1/chat/completions")) {
            return safeBaseUrl;
        }
        if (safeBaseUrl.endsWith("/v1")) {
            return safeBaseUrl + "/chat/completions";
        }
        return safeBaseUrl + "/v1/chat/completions";
    }

    private String extractNodeText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return AgentStringUtils.trimToEmpty(node.asText());
        }
        if (node.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode child : node) {
                String text = extractNodeText(child.path("text"));
                if (text.isBlank()) {
                    text = extractNodeText(child);
                }
                if (!text.isBlank()) {
                    if (!builder.isEmpty()) {
                        builder.append('\n');
                    }
                    builder.append(text);
                }
            }
            return AgentStringUtils.trimToEmpty(builder.toString());
        }
        return "";
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

        @Tool("检查本地路径是否存在及类型，客户端执行")
        public Map<String, Object> fileExists(@P("path") String path) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (path != null && !path.isBlank()) {
                arguments.put("path", AgentStringUtils.trimToEmpty(path));
            }
            return invoke("file.exists", arguments);
        }

        @Tool("读取本地路径元信息（大小/时间戳/类型），客户端执行")
        public Map<String, Object> fileStat(@P("path") String path) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (path != null && !path.isBlank()) {
                arguments.put("path", AgentStringUtils.trimToEmpty(path));
            }
            return invoke("file.stat", arguments);
        }

        @Tool("创建本地目录，客户端执行")
        public Map<String, Object> fileMkdir(@P("path") String path,
                                              @P("recursive") Boolean recursive) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (path != null && !path.isBlank()) {
                arguments.put("path", AgentStringUtils.trimToEmpty(path));
            }
            if (recursive != null) {
                arguments.put("recursive", recursive);
            }
            return invoke("file.mkdir", arguments);
        }

        @Tool("按行读取本地文本文件片段，客户端执行")
        public Map<String, Object> fileReadLines(@P("path") String path,
                                                 @P("startLine") Integer startLine,
                                                 @P("endLine") Integer endLine) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (path != null && !path.isBlank()) {
                arguments.put("path", AgentStringUtils.trimToEmpty(path));
            }
            if (startLine != null) {
                arguments.put("startLine", Math.max(1, startLine));
            }
            if (endLine != null) {
                arguments.put("endLine", Math.max(1, endLine));
            }
            return invoke("file.read.lines", arguments);
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

        @Tool("通过 Windows CMD (cmd.exe) 执行命令，仅限 CMD 语法（dir/type/copy/del/set/echo），禁止使用 PowerShell cmdlet 或 bash 命令")
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

        @Tool("重命名或移动本地文件/目录，客户端执行")
        public Map<String, Object> fileRename(@P("oldPath") String oldPath,
                                               @P("newPath") String newPath) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (oldPath != null && !oldPath.isBlank()) {
                arguments.put("oldPath", AgentStringUtils.trimToEmpty(oldPath));
            }
            if (newPath != null && !newPath.isBlank()) {
                arguments.put("newPath", AgentStringUtils.trimToEmpty(newPath));
            }
            return invoke("file.rename", arguments);
        }

        @Tool("复制本地文件或目录，客户端执行")
        public Map<String, Object> fileCopy(@P("source") String source,
                                             @P("destination") String destination) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (source != null && !source.isBlank()) {
                arguments.put("source", AgentStringUtils.trimToEmpty(source));
            }
            if (destination != null && !destination.isBlank()) {
                arguments.put("destination", AgentStringUtils.trimToEmpty(destination));
            }
            return invoke("file.copy", arguments);
        }

        @Tool("追加内容到本地文本文件末尾，客户端执行")
        public Map<String, Object> fileAppend(@P("path") String path,
                                               @P("content") String content) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (path != null && !path.isBlank()) {
                arguments.put("path", AgentStringUtils.trimToEmpty(path));
            }
            arguments.put("content", content == null ? "" : content);
            return invoke("file.append", arguments);
        }

        @Tool("在本地文件中查找并替换文本，客户端执行")
        public Map<String, Object> fileReplace(@P("path") String path,
                                                @P("search") String search,
                                                @P("replacement") String replacement,
                                                @P("replaceAll") Boolean replaceAll) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (path != null && !path.isBlank()) {
                arguments.put("path", AgentStringUtils.trimToEmpty(path));
            }
            if (search != null && !search.isBlank()) {
                arguments.put("search", search);
            }
            arguments.put("replacement", replacement == null ? "" : replacement);
            if (replaceAll != null) {
                arguments.put("replaceAll", replaceAll);
            }
            return invoke("file.replace", arguments);
        }

        @Tool("通过 Windows PowerShell (powershell.exe) 执行命令，仅限 PowerShell 语法（Get-ChildItem/Get-Content/Copy-Item 等 cmdlet），禁止使用 CMD 内部命令或 bash 命令")
        public Map<String, Object> cmdPowershell(@P("command") String command,
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
            return invoke("cmd.powershell", arguments);
        }

        @Tool("获取客户端系统信息（OS/CPU/内存/主机名），客户端执行")
        public Map<String, Object> sysInfo() {
            return invoke("sys.info", Map.of());
        }

        @Tool("查询客户端环境变量，客户端执行")
        public Map<String, Object> sysEnv(@P("name") String name,
                                           @P("filter") String filter,
                                           @P("limit") Integer limit) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (name != null && !name.isBlank()) {
                arguments.put("name", AgentStringUtils.trimToEmpty(name));
            }
            if (filter != null && !filter.isBlank()) {
                arguments.put("filter", AgentStringUtils.trimToEmpty(filter));
            }
            if (limit != null) {
                arguments.put("limit", Math.max(1, Math.min(200, limit)));
            }
            return invoke("sys.env", arguments);
        }

        @Tool("打开 Windows 系统组件（资源管理器、设置、控制面板、任务管理器等），target 为预定义名称或 ms-settings: URI，explorer 可选 path 打开指定目录")
        public Map<String, Object> sysOpen(@P("target") String target,
                                            @P("path") String path) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (target != null && !target.isBlank()) {
                arguments.put("target", AgentStringUtils.trimToEmpty(target));
            }
            if (path != null && !path.isBlank()) {
                arguments.put("path", AgentStringUtils.trimToEmpty(path));
            }
            return invoke("sys.open", arguments);
        }

        @Tool("查询 Windows 已安装程序列表，可按名称或发布者筛选，返回 name/version/publisher/installDate/installLocation，客户端执行")
        public Map<String, Object> sysInstalledApps(@P("filter") String filter,
                                                     @P("limit") Integer limit) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (filter != null && !filter.isBlank()) {
                arguments.put("filter", AgentStringUtils.trimToEmpty(filter));
            }
            if (limit != null) {
                arguments.put("limit", Math.max(1, Math.min(500, limit)));
            }
            return invoke("sys.installed-apps", arguments);
        }

        @Tool("启动程序、打开文件或 URL（用默认关联程序），参数 target 为文件路径/URL/exe路径，app 可选指定用哪个程序打开，客户端执行")
        public Map<String, Object> sysLaunch(@P("target") String target,
                                              @P("app") String app) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (target != null && !target.isBlank()) {
                arguments.put("target", AgentStringUtils.trimToEmpty(target));
            }
            if (app != null && !app.isBlank()) {
                arguments.put("app", AgentStringUtils.trimToEmpty(app));
            }
            return invoke("sys.launch", arguments);
        }

        @Tool("获取本地目录树形结构，客户端执行")
        public Map<String, Object> fileTree(@P("path") String path,
                                             @P("maxDepth") Integer maxDepth,
                                             @P("limit") Integer limit) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (path != null && !path.isBlank()) {
                arguments.put("path", AgentStringUtils.trimToEmpty(path));
            }
            if (maxDepth != null) {
                arguments.put("maxDepth", Math.max(1, Math.min(6, maxDepth)));
            }
            if (limit != null) {
                arguments.put("limit", Math.max(1, Math.min(500, limit)));
            }
            return invoke("file.tree", arguments);
        }

        @Tool("列出当前所有可见窗口及其进程信息（基于 get-windows），可选 filter 过滤进程名或窗口标题")
        public Map<String, Object> winList(@P("filter") String filter) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (filter != null && !filter.isBlank()) {
                arguments.put("filter", AgentStringUtils.trimToEmpty(filter));
            }
            return invoke("win.list", arguments);
        }

        @Tool("最小化指定窗口，高风险需用户授权。通过 pid、name 或 handle 定位目标窗口")
        public Map<String, Object> winMinimize(@P("pid") Integer pid,
                                                @P("name") String name,
                                                @P("handle") Long handle) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (pid != null) arguments.put("pid", pid);
            if (name != null && !name.isBlank()) arguments.put("name", AgentStringUtils.trimToEmpty(name));
            if (handle != null) arguments.put("handle", handle);
            return invoke("win.minimize", arguments);
        }

        @Tool("最大化指定窗口，高风险需用户授权。通过 pid、name 或 handle 定位目标窗口")
        public Map<String, Object> winMaximize(@P("pid") Integer pid,
                                                @P("name") String name,
                                                @P("handle") Long handle) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (pid != null) arguments.put("pid", pid);
            if (name != null && !name.isBlank()) arguments.put("name", AgentStringUtils.trimToEmpty(name));
            if (handle != null) arguments.put("handle", handle);
            return invoke("win.maximize", arguments);
        }

        @Tool("还原指定窗口（从最小化或最大化恢复），高风险需用户授权。通过 pid、name 或 handle 定位目标窗口")
        public Map<String, Object> winRestore(@P("pid") Integer pid,
                                               @P("name") String name,
                                               @P("handle") Long handle) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (pid != null) arguments.put("pid", pid);
            if (name != null && !name.isBlank()) arguments.put("name", AgentStringUtils.trimToEmpty(name));
            if (handle != null) arguments.put("handle", handle);
            return invoke("win.restore", arguments);
        }

        @Tool("关闭/终止指定程序进程，高风险需用户授权。通过 pid 或 name 定位目标进程")
        public Map<String, Object> winClose(@P("pid") Integer pid,
                                             @P("name") String name) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (pid != null) arguments.put("pid", pid);
            if (name != null && !name.isBlank()) arguments.put("name", AgentStringUtils.trimToEmpty(name));
            return invoke("win.close", arguments);
        }

        @Tool("截取当前屏幕并保存到工作区，客户端执行")
        public Map<String, Object> winScreenshot(@P("path") String path) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (path != null && !path.isBlank()) {
                arguments.put("path", AgentStringUtils.trimToEmpty(path));
            }
            return invoke("win.screenshot", arguments);
        }

        @Tool("读取剪贴板文本或图片，客户端执行")
        public Map<String, Object> clipboardRead() {
            return invoke("clipboard.read", Map.of());
        }

        @Tool("写入文本到剪贴板，客户端执行")
        public Map<String, Object> clipboardWrite(@P("text") String text) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            arguments.put("text", text == null ? "" : text);
            return invoke("clipboard.write", arguments);
        }

        @Tool("发送 Windows 通知，客户端执行")
        public Map<String, Object> notificationSend(@P("title") String title,
                                                    @P("body") String body) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (title != null && !title.isBlank()) {
                arguments.put("title", AgentStringUtils.trimToEmpty(title));
            }
            if (body != null && !body.isBlank()) {
                arguments.put("body", AgentStringUtils.trimToEmpty(body));
            }
            return invoke("notification.send", arguments);
        }

        @Tool("压缩文件或目录为 zip，客户端执行")
        public Map<String, Object> fileCompress(@P("path") String path,
                                                @P("destination") String destination) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (path != null && !path.isBlank()) {
                arguments.put("path", AgentStringUtils.trimToEmpty(path));
            }
            if (destination != null && !destination.isBlank()) {
                arguments.put("destination", AgentStringUtils.trimToEmpty(destination));
            }
            return invoke("file.compress", arguments);
        }

        @Tool("解压 zip 文件，客户端执行")
        public Map<String, Object> fileExtract(@P("path") String path,
                                               @P("destination") String destination) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (path != null && !path.isBlank()) {
                arguments.put("path", AgentStringUtils.trimToEmpty(path));
            }
            if (destination != null && !destination.isBlank()) {
                arguments.put("destination", AgentStringUtils.trimToEmpty(destination));
            }
            return invoke("file.extract", arguments);
        }

        @Tool("计算文件哈希，客户端执行")
        public Map<String, Object> fileHash(@P("path") String path,
                                            @P("algorithm") String algorithm) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (path != null && !path.isBlank()) {
                arguments.put("path", AgentStringUtils.trimToEmpty(path));
            }
            if (algorithm != null && !algorithm.isBlank()) {
                arguments.put("algorithm", AgentStringUtils.trimToEmpty(algorithm));
            }
            return invoke("file.hash", arguments);
        }

        @Tool("将文件或目录移到回收站，客户端执行")
        public Map<String, Object> fileTrash(@P("path") String path) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (path != null && !path.isBlank()) {
                arguments.put("path", AgentStringUtils.trimToEmpty(path));
            }
            return invoke("file.trash", arguments);
        }

        @Tool("Ping 测试，客户端执行")
        public Map<String, Object> netPing(@P("host") String host,
                                           @P("count") Integer count) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (host != null && !host.isBlank()) {
                arguments.put("host", AgentStringUtils.trimToEmpty(host));
            }
            if (count != null) {
                arguments.put("count", Math.max(1, Math.min(10, count)));
            }
            return invoke("net.ping", arguments);
        }

        @Tool("DNS 查询，客户端执行")
        public Map<String, Object> netDns(@P("host") String host) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (host != null && !host.isBlank()) {
                arguments.put("host", AgentStringUtils.trimToEmpty(host));
            }
            return invoke("net.dns", arguments);
        }

        @Tool("查询监听端口，客户端执行")
        public Map<String, Object> netPorts(@P("filter") String filter) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (filter != null && !filter.isBlank()) {
                arguments.put("filter", AgentStringUtils.trimToEmpty(filter));
            }
            return invoke("net.ports", arguments);
        }

        @Tool("管理 Windows 代理设置，客户端执行")
        public Map<String, Object> netProxy(@P("action") String action,
                                            @P("server") String server) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (action != null && !action.isBlank()) {
                arguments.put("action", AgentStringUtils.trimToEmpty(action));
            }
            if (server != null && !server.isBlank()) {
                arguments.put("server", AgentStringUtils.trimToEmpty(server));
            }
            return invoke("net.proxy", arguments);
        }

        @Tool("读取或追加 hosts 记录，客户端执行")
        public Map<String, Object> netHosts(@P("action") String action,
                                            @P("ip") String ip,
                                            @P("host") String host) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (action != null && !action.isBlank()) {
                arguments.put("action", AgentStringUtils.trimToEmpty(action));
            }
            if (ip != null && !ip.isBlank()) {
                arguments.put("ip", AgentStringUtils.trimToEmpty(ip));
            }
            if (host != null && !host.isBlank()) {
                arguments.put("host", AgentStringUtils.trimToEmpty(host));
            }
            return invoke("net.hosts", arguments);
        }

        @Tool("获取 CPU 信息和负载，客户端执行")
        public Map<String, Object> monitorCpu() {
            return invoke("monitor.cpu", Map.of());
        }

        @Tool("获取内存使用情况，客户端执行")
        public Map<String, Object> monitorMemory() {
            return invoke("monitor.memory", Map.of());
        }

        @Tool("获取磁盘空间信息，客户端执行")
        public Map<String, Object> monitorDisk() {
            return invoke("monitor.disk", Map.of());
        }

        @Tool("获取 GPU 信息，客户端执行")
        public Map<String, Object> monitorGpu() {
            return invoke("monitor.gpu", Map.of());
        }

        @Tool("获取系统音量，客户端执行")
        public Map<String, Object> volumeGet() {
            return invoke("volume.get", Map.of());
        }

        @Tool("设置系统音量，客户端执行")
        public Map<String, Object> volumeSet(@P("level") Integer level) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (level != null) {
                arguments.put("level", Math.max(0, Math.min(100, level)));
            }
            return invoke("volume.set", arguments);
        }

        @Tool("获取屏幕亮度，客户端执行")
        public Map<String, Object> brightnessGet() {
            return invoke("brightness.get", Map.of());
        }

        @Tool("设置屏幕亮度，客户端执行")
        public Map<String, Object> brightnessSet(@P("level") Integer level) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (level != null) {
                arguments.put("level", Math.max(0, Math.min(100, level)));
            }
            return invoke("brightness.set", arguments);
        }

        @Tool("列出显示器信息，客户端执行")
        public Map<String, Object> displayList() {
            return invoke("display.list", Map.of());
        }

        @Tool("让电脑进入睡眠，客户端执行")
        public Map<String, Object> powerSleep() {
            return invoke("power.sleep", Map.of());
        }

        @Tool("关闭电脑，客户端执行")
        public Map<String, Object> powerShutdown() {
            return invoke("power.shutdown", Map.of());
        }

        @Tool("重启电脑，客户端执行")
        public Map<String, Object> powerRestart() {
            return invoke("power.restart", Map.of());
        }

        @Tool("扫描可用 Wi-Fi 网络，客户端执行")
        public Map<String, Object> wifiList() {
            return invoke("wifi.list", Map.of());
        }

        @Tool("读取注册表键值，客户端执行")
        public Map<String, Object> registryRead(@P("path") String path,
                                                @P("name") String name) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (path != null && !path.isBlank()) {
                arguments.put("path", AgentStringUtils.trimToEmpty(path));
            }
            if (name != null && !name.isBlank()) {
                arguments.put("name", AgentStringUtils.trimToEmpty(name));
            }
            return invoke("registry.read", arguments);
        }

        @Tool("写入注册表键值，客户端执行")
        public Map<String, Object> registryWrite(@P("path") String path,
                                                 @P("name") String name,
                                                 @P("value") String value,
                                                 @P("type") String type) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (path != null && !path.isBlank()) {
                arguments.put("path", AgentStringUtils.trimToEmpty(path));
            }
            if (name != null && !name.isBlank()) {
                arguments.put("name", AgentStringUtils.trimToEmpty(name));
            }
            if (value != null) {
                arguments.put("value", value);
            }
            if (type != null && !type.isBlank()) {
                arguments.put("type", AgentStringUtils.trimToEmpty(type));
            }
            return invoke("registry.write", arguments);
        }

        @Tool("删除注册表键或值，客户端执行")
        public Map<String, Object> registryDelete(@P("path") String path,
                                                  @P("name") String name) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (path != null && !path.isBlank()) {
                arguments.put("path", AgentStringUtils.trimToEmpty(path));
            }
            if (name != null && !name.isBlank()) {
                arguments.put("name", AgentStringUtils.trimToEmpty(name));
            }
            return invoke("registry.delete", arguments);
        }

        @Tool("列出 Windows 服务，客户端执行")
        public Map<String, Object> serviceList(@P("filter") String filter) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (filter != null && !filter.isBlank()) {
                arguments.put("filter", AgentStringUtils.trimToEmpty(filter));
            }
            return invoke("service.list", arguments);
        }

        @Tool("启动 Windows 服务，客户端执行")
        public Map<String, Object> serviceStart(@P("name") String name) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (name != null && !name.isBlank()) {
                arguments.put("name", AgentStringUtils.trimToEmpty(name));
            }
            return invoke("service.start", arguments);
        }

        @Tool("停止 Windows 服务，客户端执行")
        public Map<String, Object> serviceStop(@P("name") String name) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (name != null && !name.isBlank()) {
                arguments.put("name", AgentStringUtils.trimToEmpty(name));
            }
            return invoke("service.stop", arguments);
        }

        @Tool("重启 Windows 服务，客户端执行")
        public Map<String, Object> serviceRestart(@P("name") String name) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (name != null && !name.isBlank()) {
                arguments.put("name", AgentStringUtils.trimToEmpty(name));
            }
            return invoke("service.restart", arguments);
        }

        @Tool("列出 Windows 计划任务，客户端执行")
        public Map<String, Object> scheduleTaskList(@P("filter") String filter) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (filter != null && !filter.isBlank()) {
                arguments.put("filter", AgentStringUtils.trimToEmpty(filter));
            }
            return invoke("schedule.task.list", arguments);
        }

        @Tool("创建 Windows 计划任务，客户端执行")
        public Map<String, Object> scheduleTaskCreate(@P("name") String name,
                                                      @P("command") String command,
                                                      @P("trigger") String trigger,
                                                      @P("time") String time) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (name != null && !name.isBlank()) {
                arguments.put("name", AgentStringUtils.trimToEmpty(name));
            }
            if (command != null && !command.isBlank()) {
                arguments.put("command", command);
            }
            if (trigger != null && !trigger.isBlank()) {
                arguments.put("trigger", AgentStringUtils.trimToEmpty(trigger));
            }
            if (time != null && !time.isBlank()) {
                arguments.put("time", AgentStringUtils.trimToEmpty(time));
            }
            return invoke("schedule.task.create", arguments);
        }

        @Tool("列出 Windows 防火墙规则，客户端执行")
        public Map<String, Object> firewallRules(@P("filter") String filter) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (filter != null && !filter.isBlank()) {
                arguments.put("filter", AgentStringUtils.trimToEmpty(filter));
            }
            return invoke("firewall.rules", arguments);
        }

        @Tool("触发 Windows Defender 扫描，客户端执行")
        public Map<String, Object> defenderScan(@P("type") String type) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (type != null && !type.isBlank()) {
                arguments.put("type", AgentStringUtils.trimToEmpty(type));
            }
            return invoke("defender.scan", arguments);
        }

        @Tool("列出 eIsland 全部可控设置项及当前值，客户端执行")
        public Map<String, Object> islandSettingsList() {
            return invoke("island.settings.list", Map.of());
        }

        @Tool("读取 eIsland 指定设置项，客户端执行")
        public Map<String, Object> islandSettingsRead(@P("key") String key) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (key != null && !key.isBlank()) {
                arguments.put("key", AgentStringUtils.trimToEmpty(key));
            }
            return invoke("island.settings.read", arguments);
        }

        @Tool("写入 eIsland 指定设置项并实时生效，客户端执行。先用 islandSettingsList 查看可用 key")
        public Map<String, Object> islandSettingsWrite(@P("key") String key,
                                                       @P("value") Object value) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (key != null && !key.isBlank()) {
                arguments.put("key", AgentStringUtils.trimToEmpty(key));
            }
            arguments.put("value", value);
            return invoke("island.settings.write", arguments);
        }

        @Tool("获取 eIsland 当前主题模式（dark/light/system），客户端执行")
        public Map<String, Object> islandThemeGet() {
            return invoke("island.theme.get", Map.of());
        }

        @Tool("设置 eIsland 主题模式（dark/light/system），立即生效，客户端执行")
        public Map<String, Object> islandThemeSet(@P("mode") String mode) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (mode != null && !mode.isBlank()) {
                arguments.put("mode", AgentStringUtils.trimToEmpty(mode));
            }
            return invoke("island.theme.set", arguments);
        }

        @Tool("获取 eIsland 灵动岛透明度（10-100），客户端执行")
        public Map<String, Object> islandOpacityGet() {
            return invoke("island.opacity.get", Map.of());
        }

        @Tool("设置 eIsland 灵动岛透明度（10-100），立即生效，客户端执行")
        public Map<String, Object> islandOpacitySet(@P("opacity") Integer opacity) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            if (opacity != null) {
                arguments.put("opacity", Math.max(10, Math.min(100, opacity)));
            }
            return invoke("island.opacity.set", arguments);
        }

        @Tool("重启 eIsland 应用，客户端执行")
        public Map<String, Object> islandRestart() {
            return invoke("island.restart", Map.of());
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
