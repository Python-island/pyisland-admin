package com.pyisland.server.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pyisland.server.agent.config.MihtnelisAgentProperties;
import com.pyisland.server.agent.utils.AgentJsonUtils;
import com.pyisland.server.agent.utils.AgentStringUtils;
import com.pyisland.server.user.entity.User;
import com.pyisland.server.user.service.UserService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * mihtnelis agent 编排服务（Phase B 骨架）。
 */
@Service
public class MihtnelisAgentOrchestratorService {

    private static final int MAX_REACT_TURNS = 5;
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final Pattern THINK_TAG_PATTERN = Pattern.compile("<think>.*?</think>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern THINK_TAG_CAPTURE_PATTERN = Pattern.compile("<think>(.*?)</think>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private final AiProviderRouterService providerRouterService;
    private final MihtnelisAgentProperties properties;
    private final UserService userService;
    private final LangChainWorkflowService workflowService;
    private final AgentToolExecutionService toolExecutionService;
    private final AgentLocalToolRelayService localToolRelayService;
    private final AgentChatGatewayService chatGatewayService;
    private final ObjectMapper objectMapper;

    public MihtnelisAgentOrchestratorService(AiProviderRouterService providerRouterService,
                                             MihtnelisAgentProperties properties,
                                             UserService userService,
                                             LangChainWorkflowService workflowService,
                                             AgentToolExecutionService toolExecutionService,
                                             AgentLocalToolRelayService localToolRelayService,
                                             AgentChatGatewayService chatGatewayService) {
        this.providerRouterService = providerRouterService;
        this.properties = properties;
        this.userService = userService;
        this.workflowService = workflowService;
        this.toolExecutionService = toolExecutionService;
        this.localToolRelayService = localToolRelayService;
        this.chatGatewayService = chatGatewayService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 生成本轮编排执行结果。
     *
     * @param username 用户名。
     * @param request  请求。
     * @return 编排结果。
     */
    public AgentExecutionResult orchestrate(String username,
                                            String clientIp,
                                            MihtnelisAgentStreamService.MihtnelisStreamRequest request) {
        return orchestrate(username, clientIp, request, null);
    }

    public AgentExecutionResult orchestrate(String username,
                                            String clientIp,
                                            MihtnelisAgentStreamService.MihtnelisStreamRequest request,
                                            AgentToolExecutionService.ToolExecutionObserver toolExecutionObserver) {
        String provider = providerRouterService.resolveProvider(request == null ? null : request.provider());
        if ("auto".equalsIgnoreCase(provider)) {
            provider = AgentStringUtils.trimToDefault(properties.getDefaultProvider(), "deepseek");
        }
        User user = username == null || username.isBlank() ? null : userService.getByUsername(username);
        boolean proUser = isProUser(user);
        String userPrompt = request == null ? "" : AgentStringUtils.trimToDefault(request.message(), "");
        String contextPrompt = request == null ? "" : AgentStringUtils.trimToDefault(request.context(), "");
        MihtnelisAgentProperties.Provider providerConfig = resolveProviderConfig(provider);
        AgentChatGatewayService.ChatRequestOptions chatRequestOptions = resolveChatRequestOptions(request, providerConfig);

        AgentToolExecutionService.ExecutionContext executionContext =
                new AgentToolExecutionService.ExecutionContext(username, clientIp, toolExecutionObserver);
        List<ToolInvocationTrace> traces = new java.util.ArrayList<>();

        if (chatGatewayService.supportsNativeToolCalling()) {
            String nativeSystemPrompt = workflowService.buildNativeToolSystemPrompt(proUser);
            String nativeUserPrompt = workflowService.buildUserPrompt(userPrompt, contextPrompt, provider);
            String answer = chatGatewayService.chatWithNativeTools(
                    provider,
                    nativeSystemPrompt,
                    nativeUserPrompt,
                    toolExecutionService,
                    proUser,
                    executionContext,
                    chatRequestOptions
            );
            return AgentExecutionResult.done(provider, AgentStringUtils.trimToDefault(answer, ""), proUser, traces);
        }

        String systemPrompt = workflowService.buildSystemPrompt(proUser);
        String scratchpad = "";
        for (int turn = 1; turn <= MAX_REACT_TURNS; turn++) {
            String gatewayPrompt = workflowService.buildReActUserPrompt(userPrompt, contextPrompt, provider, scratchpad);
            String llmOutput = chatGatewayService.chat(provider, systemPrompt, gatewayPrompt, chatRequestOptions);
            notifyThinking(executionContext, turn, llmOutput);
            ReActDecision decision = parseDecision(llmOutput);

            if (!decision.toolCall()) {
                String finalAnswer = AgentStringUtils.trimToDefault(decision.finalAnswer(), "");
                if (!finalAnswer.isBlank()) {
                    return AgentExecutionResult.done(provider, finalAnswer, proUser, traces);
                }
                String fallbackFromRaw = AgentStringUtils.trimToDefault(llmOutput, "");
                if (!fallbackFromRaw.isBlank()) {
                    return AgentExecutionResult.done(provider, fallbackFromRaw, proUser, traces);
                }
                break;
            }

            AgentToolExecutionService.ToolResult toolResult = toolExecutionService.execute(
                    decision.toolName(),
                    decision.arguments(),
                    proUser,
                    executionContext
            );
            if (isPendingWebAccess(toolResult)) {
                PendingWebAccess pendingWebAccess = extractPendingWebAccess(toolResult);
                if (pendingWebAccess != null) {
                    return AgentExecutionResult.paused(provider, proUser, traces, pendingWebAccess);
                }
            }
            if (isPendingLocalTool(toolResult)) {
                PendingLocalTool pendingLocalTool = extractPendingLocalTool(
                        executionContext,
                        decision.toolName(),
                        decision.arguments(),
                        toolResult
                );
                if (pendingLocalTool != null) {
                    return AgentExecutionResult.pausedForLocalTool(provider, proUser, traces, pendingLocalTool);
                }
            }
            traces.add(new ToolInvocationTrace(
                    turn,
                    AgentStringUtils.trimToDefault(decision.toolName(), "unknown"),
                    decision.arguments(),
                    toolResult.success(),
                    toolResult.error(),
                    toolResult.data()
            ));
            scratchpad = appendObservation(scratchpad, turn, decision.toolName(), decision.arguments(), toolResult);
        }

        String fallbackMessage = resolveFallbackMessage(provider, traces);
        return AgentExecutionResult.done(provider, fallbackMessage, proUser, traces);
    }

    private ReActDecision parseDecision(String llmOutput) {
        String output = AgentStringUtils.trimToDefault(llmOutput, "");
        if (output.isBlank()) {
            return ReActDecision.finalAnswer("");
        }
        String normalizedOutput = stripThinkBlocks(output);
        if (normalizedOutput.isBlank()) {
            return ReActDecision.finalAnswer("");
        }
        // 模型可能返回字符串字面量内带未转义换行的 JSON，先严格解析，失败则尝试修复后再解析。
        for (String candidate : new String[]{normalizedOutput, AgentJsonUtils.repairLiteralNewlinesInStrings(normalizedOutput)}) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            try {
                Map<String, Object> payload = objectMapper.readValue(candidate, MAP_TYPE);
                String type = AgentStringUtils.trimToDefault(AgentStringUtils.toStringValue(payload.get("type")), "").toLowerCase(Locale.ROOT);
                if ("tool_call".equals(type)) {
                    String tool = AgentStringUtils.trimToDefault(AgentStringUtils.toStringValue(payload.get("tool")), "");
                    Map<String, Object> arguments = AgentJsonUtils.toStringKeyMap(payload.get("arguments"));
                    if (!tool.isBlank()) {
                        return ReActDecision.toolCall(tool, arguments);
                    }
                }
                String answer = AgentStringUtils.trimToDefault(AgentStringUtils.toStringValue(payload.get("answer")), "");
                if (!answer.isBlank()) {
                    return ReActDecision.finalAnswer(answer);
                }
            } catch (Exception ignored) {
                // try next candidate
            }
        }
        return ReActDecision.finalAnswer(normalizedOutput);
    }

    private String stripThinkBlocks(String text) {
        String source = AgentStringUtils.trimToDefault(text, "");
        if (source.isBlank()) {
            return "";
        }
        return THINK_TAG_PATTERN.matcher(source).replaceAll("").trim();
    }

    private void notifyThinking(AgentToolExecutionService.ExecutionContext context,
                                int turn,
                                String llmOutput) {
        if (context == null || context.toolExecutionObserver() == null) {
            return;
        }
        String source = AgentStringUtils.trimToDefault(llmOutput, "");
        if (source.isBlank()) {
            return;
        }
        // 同一次 chat() 内的多个 <think> 块合并为一次 onThinking 通知，
        // 与单次工具调用对齐，避免时间线混乱。
        StringBuilder merged = new StringBuilder();
        Matcher matcher = THINK_TAG_CAPTURE_PATTERN.matcher(source);
        while (matcher.find()) {
            String content = AgentStringUtils.trimToDefault(matcher.group(1), "");
            if (content.isBlank()) {
                continue;
            }
            if (merged.length() > 0) {
                merged.append("\n\n");
            }
            merged.append(content);
        }
        if (merged.length() > 0) {
            context.toolExecutionObserver().onThinking(turn, merged.toString());
        }
    }

    private String appendObservation(String scratchpad,
                                     int turn,
                                     String toolName,
                                     Map<String, Object> arguments,
                                     AgentToolExecutionService.ToolResult toolResult) {
        StringBuilder builder = new StringBuilder();
        builder.append(AgentStringUtils.trimToDefault(scratchpad, ""));
        if (builder.length() > 0) {
            builder.append("\n\n");
        }
        builder.append("Turn ").append(turn).append(':').append("\n")
                .append("Action: ").append(AgentStringUtils.trimToDefault(toolName, "unknown")).append("\n")
                .append("Action Input: ").append(AgentJsonUtils.toSafeJson(objectMapper, arguments, 1200)).append("\n")
                .append("Observation: ").append(AgentJsonUtils.toSafeJson(objectMapper, Map.of(
                        "tool", toolResult.tool(),
                        "success", toolResult.success(),
                        "data", toolResult.data(),
                        "error", toolResult.error()
                ), 8000));
        return builder.toString();
    }

    private boolean isProUser(User user) {
        if (user == null || user.getRole() == null) {
            return false;
        }
        String role = user.getRole().trim().toLowerCase(Locale.ROOT);
        return User.ROLE_PRO.equals(role) || User.ROLE_ADMIN.equals(role);
    }

    private MihtnelisAgentProperties.Provider resolveProviderConfig(String provider) {
        String safeProvider = AgentStringUtils.trimToEmpty(provider).toLowerCase(Locale.ROOT);
        MihtnelisAgentProperties.Llm llm = properties.getLlm();
        if (llm == null) {
            return null;
        }
        if ("deepseek".equals(safeProvider) || safeProvider.isBlank()) {
            return llm.getDeepseek();
        }
        return llm.getDeepseek();
    }

    private AgentChatGatewayService.ChatRequestOptions resolveChatRequestOptions(
            MihtnelisAgentStreamService.MihtnelisStreamRequest request,
            MihtnelisAgentProperties.Provider providerConfig) {
        boolean defaultThinking = providerConfig != null && providerConfig.isThinking();
        String defaultReasoningEffort = normalizeReasoningEffort(providerConfig == null ? "" : providerConfig.getReasoningEffort());
        Boolean requestThinking = request == null ? null : request.thinking();
        String requestReasoningEffort = request == null ? "" : AgentStringUtils.trimToEmpty(request.reasoningEffort());
        boolean effectiveThinking = requestThinking == null ? defaultThinking : requestThinking;
        String effectiveReasoningEffort = requestReasoningEffort.isBlank()
                ? defaultReasoningEffort
                : normalizeReasoningEffort(requestReasoningEffort);
        return new AgentChatGatewayService.ChatRequestOptions(effectiveThinking, effectiveReasoningEffort);
    }

    private String normalizeReasoningEffort(String value) {
        String candidate = AgentStringUtils.trimToEmpty(value).toLowerCase(Locale.ROOT);
        if ("low".equals(candidate) || "high".equals(candidate)) {
            return candidate;
        }
        return "medium";
    }

    private String resolveFallbackMessage(String provider, List<ToolInvocationTrace> traces) {
        if (traces != null && !traces.isEmpty()) {
            ToolInvocationTrace lastTrace = traces.get(traces.size() - 1);
            String tool = AgentStringUtils.trimToDefault(lastTrace.tool(), "unknown");
            String error = AgentStringUtils.trimToDefault(lastTrace.error(), "");
            if (!error.isBlank()) {
                return "工具调用失败：" + tool + "，原因：" + error;
            }
            if (lastTrace.success()) {
                return "已完成工具调用：" + tool + "，但模型未生成最终结论。请继续追问“请基于以上工具结果给出最终回答”。";
            }
            return "工具调用未返回可用结论：" + tool + "，请重试或调整问题后再试。";
        }
        return "当前 provider=" + AgentStringUtils.trimToDefault(provider, "deepseek")
                + " 未返回有效结果，请检查模型配置与网络后重试。";
    }

    /**
     * 编排结果。
     */
    private boolean isPendingWebAccess(AgentToolExecutionService.ToolResult toolResult) {
        if (toolResult == null || !"web.page.read".equals(toolResult.tool()) || !toolResult.success()) {
            return false;
        }
        if (!(toolResult.data() instanceof Map<?, ?> map)) {
            return false;
        }
        Object required = map.get("authorizationRequired");
        if (required instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return "true".equalsIgnoreCase(String.valueOf(required));
    }

    private PendingWebAccess extractPendingWebAccess(AgentToolExecutionService.ToolResult toolResult) {
        if (toolResult == null || !(toolResult.data() instanceof Map<?, ?> map)) {
            return null;
        }
        String requestId = AgentStringUtils.trimToEmpty(AgentStringUtils.toStringValue(map.get("requestId")));
        String url = AgentStringUtils.trimToEmpty(AgentStringUtils.toStringValue(map.get("url")));
        if (requestId.isBlank() || url.isBlank()) {
            return null;
        }
        return new PendingWebAccess(requestId, url);
    }

    private boolean isPendingLocalTool(AgentToolExecutionService.ToolResult toolResult) {
        if (toolResult == null || !toolResult.success()) {
            return false;
        }
        if (!(toolResult.data() instanceof Map<?, ?> map)) {
            return false;
        }
        Object required = map.get("localToolExecutionRequired");
        if (required instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return "true".equalsIgnoreCase(String.valueOf(required));
    }

    private PendingLocalTool extractPendingLocalTool(AgentToolExecutionService.ExecutionContext context,
                                                     String toolName,
                                                     Map<String, Object> arguments,
                                                     AgentToolExecutionService.ToolResult toolResult) {
        if (context == null) {
            return null;
        }
        String username = AgentStringUtils.trimToEmpty(context.username());
        String safeToolName = AgentStringUtils.trimToEmpty(toolName);
        if (username.isBlank() || safeToolName.isBlank()) {
            return null;
        }
        Map<String, Object> safeArguments = arguments == null ? Map.of() : arguments;
        String riskLevel = resolveLocalToolRiskLevel(safeToolName);
        AgentLocalToolRelayService.PendingRequestResult pending = localToolRelayService.createPendingRequest(
                username,
                safeToolName,
                safeArguments,
                riskLevel
        );
        if (pending == null) {
            return null;
        }
        return new PendingLocalTool(
                pending.requestId(),
                pending.tool(),
                pending.arguments(),
                pending.argumentsDigest(),
                pending.riskLevel(),
                AgentStringUtils.trimToDefault(toolResult.tool(), safeToolName)
        );
    }

    private String resolveLocalToolRiskLevel(String toolName) {
        String safeToolName = AgentStringUtils.trimToEmpty(toolName).toLowerCase(Locale.ROOT);
        if (safeToolName.startsWith("file.read") || safeToolName.startsWith("file.list")) {
            return "low";
        }
        return "high";
    }

    public record AgentExecutionResult(String provider,
                                       String answer,
                                       boolean proUser,
                                       List<ToolInvocationTrace> toolInvocations,
                                       PendingWebAccess pendingWebAccess,
                                       boolean pausedForWebAccess,
                                       PendingLocalTool pendingLocalTool,
                                       boolean pausedForLocalTool) {
        public static AgentExecutionResult done(String provider,
                                                String answer,
                                                boolean proUser,
                                                List<ToolInvocationTrace> traces) {
            return new AgentExecutionResult(
                    provider,
                    AgentStringUtils.trimToDefault(answer, ""),
                    proUser,
                    traces == null ? List.of() : traces,
                    null,
                    false,
                    null,
                    false
            );
        }

        public static AgentExecutionResult paused(String provider,
                                                  boolean proUser,
                                                  List<ToolInvocationTrace> traces,
                                                  PendingWebAccess pendingWebAccess) {
            return new AgentExecutionResult(
                    provider,
                    "",
                    proUser,
                    traces == null ? List.of() : traces,
                    pendingWebAccess,
                    true,
                    null,
                    false
            );
        }

        public static AgentExecutionResult pausedForLocalTool(String provider,
                                                              boolean proUser,
                                                              List<ToolInvocationTrace> traces,
                                                              PendingLocalTool pendingLocalTool) {
            return new AgentExecutionResult(
                    provider,
                    "",
                    proUser,
                    traces == null ? List.of() : traces,
                    null,
                    false,
                    pendingLocalTool,
                    true
            );
        }
    }

    public record PendingWebAccess(String requestId, String url) {
    }

    public record PendingLocalTool(String requestId,
                                   String tool,
                                   Map<String, Object> arguments,
                                   String argumentsDigest,
                                   String riskLevel,
                                   String sourceTool) {
    }

    public record ToolInvocationTrace(int turn,
                                      String tool,
                                      Map<String, Object> arguments,
                                      boolean success,
                                      String error,
                                      Object result) {
    }

    private record ReActDecision(boolean toolCall, String toolName, Map<String, Object> arguments, String finalAnswer) {

        private static ReActDecision toolCall(String toolName, Map<String, Object> arguments) {
            return new ReActDecision(true, toolName, arguments == null ? Map.of() : arguments, "");
        }

        private static ReActDecision finalAnswer(String answer) {
            return new ReActDecision(false, "", Map.of(), answer);
        }
    }
}
