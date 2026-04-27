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

/**
 * mihtnelis agent 编排服务（Phase B 骨架）。
 */
@Service
public class MihtnelisAgentOrchestratorService {

    private static final int MAX_REACT_TURNS = 3;
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final AiProviderRouterService providerRouterService;
    private final MihtnelisAgentProperties properties;
    private final UserService userService;
    private final LangChainWorkflowService workflowService;
    private final AgentToolExecutionService toolExecutionService;
    private final AgentChatGatewayService chatGatewayService;
    private final ObjectMapper objectMapper;

    public MihtnelisAgentOrchestratorService(AiProviderRouterService providerRouterService,
                                             MihtnelisAgentProperties properties,
                                             UserService userService,
                                             LangChainWorkflowService workflowService,
                                             AgentToolExecutionService toolExecutionService,
                                             AgentChatGatewayService chatGatewayService) {
        this.providerRouterService = providerRouterService;
        this.properties = properties;
        this.userService = userService;
        this.workflowService = workflowService;
        this.toolExecutionService = toolExecutionService;
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
        String provider = providerRouterService.resolveProvider(request == null ? null : request.provider());
        if ("auto".equalsIgnoreCase(provider)) {
            provider = AgentStringUtils.trimToDefault(properties.getDefaultProvider(), "deepseek");
        }
        User user = username == null || username.isBlank() ? null : userService.getByUsername(username);
        boolean proUser = isProUser(user);
        String userPrompt = request == null ? "" : AgentStringUtils.trimToDefault(request.message(), "");

        AgentToolExecutionService.ExecutionContext executionContext =
                new AgentToolExecutionService.ExecutionContext(username, clientIp);
        List<ToolInvocationTrace> traces = new java.util.ArrayList<>();

        if (chatGatewayService.supportsNativeToolCalling()) {
            String nativeSystemPrompt = workflowService.buildNativeToolSystemPrompt(proUser);
            String nativeUserPrompt = workflowService.buildUserPrompt(userPrompt, provider);
            String answer = chatGatewayService.chatWithNativeTools(
                    provider,
                    nativeSystemPrompt,
                    nativeUserPrompt,
                    toolExecutionService,
                    proUser,
                    executionContext
            );
            return AgentExecutionResult.done(provider, AgentStringUtils.trimToDefault(answer, ""), proUser, traces);
        }

        String systemPrompt = workflowService.buildSystemPrompt(proUser);
        String scratchpad = "";
        for (int turn = 1; turn <= MAX_REACT_TURNS; turn++) {
            String gatewayPrompt = workflowService.buildReActUserPrompt(userPrompt, provider, scratchpad);
            String llmOutput = chatGatewayService.chat(provider, systemPrompt, gatewayPrompt);
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

        StringBuilder content = new StringBuilder();
        content.append("mihtnelis agent 已完成 Phase 3 编排与模型网关接线。")
                .append("当前 provider=").append(provider).append("，")
                .append("当前未读取到可用模型配置，已回退到占位输出。")
                .append("本轮可用能力：基础对话");

        if (proUser && properties.getOssVector() != null && properties.getOssVector().isEnabled()) {
            content.append("、OSS 向量检索(预留)");
        }
        content.append("。");

        return AgentExecutionResult.done(provider, content.toString(), proUser, traces);
    }

    private ReActDecision parseDecision(String llmOutput) {
        String output = AgentStringUtils.trimToDefault(llmOutput, "");
        if (output.isBlank()) {
            return ReActDecision.finalAnswer("");
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(output, MAP_TYPE);
            String type = AgentStringUtils.trimToDefault(AgentStringUtils.toStringValue(payload.get("type")), "").toLowerCase(Locale.ROOT);
            if ("tool_call".equals(type)) {
                String tool = AgentStringUtils.trimToDefault(AgentStringUtils.toStringValue(payload.get("tool")), "");
                Map<String, Object> arguments = AgentJsonUtils.toStringKeyMap(payload.get("arguments"));
                if (!tool.isBlank()) {
                    return ReActDecision.toolCall(tool, arguments);
                }
            }
            String answer = AgentStringUtils.trimToDefault(AgentStringUtils.toStringValue(payload.get("answer")), "");
            if (answer.isBlank()) {
                answer = output;
            }
            return ReActDecision.finalAnswer(answer);
        } catch (Exception ignored) {
            return ReActDecision.finalAnswer(output);
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
                ), 1200));
        return builder.toString();
    }

    private boolean isProUser(User user) {
        if (user == null || user.getRole() == null) {
            return false;
        }
        String role = user.getRole().trim().toLowerCase(Locale.ROOT);
        return User.ROLE_PRO.equals(role) || User.ROLE_ADMIN.equals(role);
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

    public record AgentExecutionResult(String provider,
                                       String answer,
                                       boolean proUser,
                                       List<ToolInvocationTrace> toolInvocations,
                                       PendingWebAccess pendingWebAccess,
                                       boolean pausedForWebAccess) {
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
                    true
            );
        }
    }

    public record PendingWebAccess(String requestId, String url) {
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
