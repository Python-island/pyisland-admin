package com.pyisland.server.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pyisland.server.agent.config.MihtnelisAgentProperties;
import com.pyisland.server.agent.utils.AgentStringUtils;
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
import java.util.Map;

/**
 * Spring AI 网关服务。
 */
@Service
@ConditionalOnProperty(prefix = "mihtnelis.agent.llm", name = "gateway", havingValue = "spring-ai", matchIfMissing = true)
public class SpringAiChatGatewayService implements AgentChatGatewayService {

    private static final Logger log = LoggerFactory.getLogger(SpringAiChatGatewayService.class);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
    public String chat(String provider,
                       String systemPrompt,
                       String userPrompt,
                       ChatRequestOptions requestOptions) {
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
            ChatRequestOptions safeRequestOptions = normalizeRequestOptions(requestOptions);
            return invokeDeepSeek(baseUrl, apiKey, model, safeSystemPrompt, safeUserPrompt, safeRequestOptions);
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

    private String invokeDeepSeek(String baseUrl,
                                  String apiKey,
                                  String model,
                                  String systemPrompt,
                                  String userPrompt,
                                  ChatRequestOptions requestOptions) throws Exception {
        boolean thinkingEnabled = requestOptions != null && requestOptions.thinkingEnabled();
        String effort = requestOptions == null
                ? "medium"
                : AgentStringUtils.trimToDefault(requestOptions.reasoningEffort(), "medium");
        String url = resolveCompletionsUrl(baseUrl);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", new Object[]{
                Map.of("role", "system", "content", AgentStringUtils.trimToEmpty(systemPrompt)),
                Map.of("role", "user", "content", AgentStringUtils.trimToEmpty(userPrompt))
        });
        payload.put("temperature", 0.2);
        payload.put("stream", false);
        if (thinkingEnabled) {
            payload.put("thinking", Map.of("type", "enabled"));
            payload.put("reasoning_effort", effort);
        }
        String requestBody = OBJECT_MAPPER.writeValueAsString(payload);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("DeepSeek HTTP " + response.statusCode() + ": " + AgentStringUtils.trimToEmpty(response.body()));
        }
        JsonNode root = OBJECT_MAPPER.readTree(response.body());
        JsonNode messageNode = root.path("choices").isArray() && root.path("choices").size() > 0
                ? root.path("choices").get(0).path("message")
                : OBJECT_MAPPER.createObjectNode();
        String content = extractNodeText(messageNode.path("content"));
        String reasoningContent = extractNodeText(messageNode.path("reasoning_content"));
        if (content.isBlank() && !reasoningContent.isBlank()) {
            content = extractNodeText(root.path("content"));
        }
        if (content.isBlank() && reasoningContent.isBlank()) {
            throw new IllegalStateException("DeepSeek returned blank text");
        }
        if (thinkingEnabled && !reasoningContent.isBlank()) {
            return "<think>" + reasoningContent + "</think>\n" + content;
        }
        return content;
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
                    if (builder.length() > 0) {
                        builder.append('\n');
                    }
                    builder.append(text);
                }
            }
            return AgentStringUtils.trimToEmpty(builder.toString());
        }
        return "";
    }
}
