package com.pyisland.server.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pyisland.server.agent.config.MihtnelisAgentProperties;
import com.pyisland.server.user.entity.User;
import com.pyisland.server.user.service.UserService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
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
            provider = normalize(properties.getDefaultProvider(), "deepseek");
        }
        User user = username == null || username.isBlank() ? null : userService.getByUsername(username);
        boolean proUser = isProUser(user);
        String userPrompt = request == null ? "" : normalize(request.message(), "");

        AgentToolExecutionService.ExecutionContext executionContext =
                new AgentToolExecutionService.ExecutionContext(username, clientIp);

        String systemPrompt = workflowService.buildSystemPrompt(proUser);
        String scratchpad = "";
        for (int turn = 1; turn <= MAX_REACT_TURNS; turn++) {
            String gatewayPrompt = workflowService.buildReActUserPrompt(userPrompt, provider, scratchpad);
            String llmOutput = chatGatewayService.chat(provider, systemPrompt, gatewayPrompt);
            ReActDecision decision = parseDecision(llmOutput);

            if (!decision.toolCall()) {
                String finalAnswer = normalize(decision.finalAnswer(), "");
                if (!finalAnswer.isBlank()) {
                    return new AgentExecutionResult(provider, finalAnswer, proUser);
                }
                String fallbackFromRaw = normalize(llmOutput, "");
                if (!fallbackFromRaw.isBlank()) {
                    return new AgentExecutionResult(provider, fallbackFromRaw, proUser);
                }
                break;
            }

            AgentToolExecutionService.ToolResult toolResult = toolExecutionService.execute(
                    decision.toolName(),
                    decision.arguments(),
                    proUser,
                    executionContext
            );
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

        return new AgentExecutionResult(provider, content.toString(), proUser);
    }

    private String normalize(String value, String fallback) {
        String safe = value == null ? "" : value.trim();
        return safe.isBlank() ? fallback : safe;
    }

    private ReActDecision parseDecision(String llmOutput) {
        String output = normalize(llmOutput, "");
        if (output.isBlank()) {
            return ReActDecision.finalAnswer("");
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(output, MAP_TYPE);
            String type = normalize(value(payload.get("type")), "").toLowerCase(Locale.ROOT);
            if ("tool_call".equals(type)) {
                String tool = normalize(value(payload.get("tool")), "");
                Map<String, Object> arguments = toMap(payload.get("arguments"));
                if (!tool.isBlank()) {
                    return ReActDecision.toolCall(tool, arguments);
                }
            }
            String answer = normalize(value(payload.get("answer")), "");
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
        builder.append(normalize(scratchpad, ""));
        if (builder.length() > 0) {
            builder.append("\n\n");
        }
        builder.append("Turn ").append(turn).append(':').append("\n")
                .append("Action: ").append(normalize(toolName, "unknown")).append("\n")
                .append("Action Input: ").append(safeJson(arguments)).append("\n")
                .append("Observation: ").append(safeJson(Map.of(
                        "tool", toolResult.tool(),
                        "success", toolResult.success(),
                        "data", toolResult.data(),
                        "error", toolResult.error()
                )));
        return builder.toString();
    }

    private Map<String, Object> toMap(Object value) {
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                String key = entry.getKey() == null ? "" : String.valueOf(entry.getKey());
                map.put(key, entry.getValue());
            }
            return map;
        }
        return Map.of();
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String safeJson(Object value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            if (json.length() <= 1200) {
                return json;
            }
            return json.substring(0, 1200);
        } catch (Exception ignored) {
            return "{}";
        }
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
    public record AgentExecutionResult(String provider, String answer, boolean proUser) {
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
