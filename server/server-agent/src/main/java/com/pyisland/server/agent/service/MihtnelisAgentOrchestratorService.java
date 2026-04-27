package com.pyisland.server.agent.service;

import com.pyisland.server.agent.config.MihtnelisAgentProperties;
import com.pyisland.server.user.entity.User;
import com.pyisland.server.user.service.UserService;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * mihtnelis agent 编排服务（Phase B 骨架）。
 */
@Service
public class MihtnelisAgentOrchestratorService {

    private final AiProviderRouterService providerRouterService;
    private final MihtnelisAgentProperties properties;
    private final UserService userService;
    private final LangChainWorkflowService workflowService;
    private final SpringAiChatGatewayService chatGatewayService;

    public MihtnelisAgentOrchestratorService(AiProviderRouterService providerRouterService,
                                             MihtnelisAgentProperties properties,
                                             UserService userService,
                                             LangChainWorkflowService workflowService,
                                             SpringAiChatGatewayService chatGatewayService) {
        this.providerRouterService = providerRouterService;
        this.properties = properties;
        this.userService = userService;
        this.workflowService = workflowService;
        this.chatGatewayService = chatGatewayService;
    }

    /**
     * 生成本轮编排执行结果。
     *
     * @param username 用户名。
     * @param request  请求。
     * @return 编排结果。
     */
    public AgentExecutionResult orchestrate(String username,
                                            MihtnelisAgentStreamService.MihtnelisStreamRequest request) {
        String provider = providerRouterService.resolveProvider(request == null ? null : request.provider());
        if ("auto".equalsIgnoreCase(provider)) {
            provider = normalize(properties.getDefaultProvider(), "deepseek");
        }
        User user = username == null || username.isBlank() ? null : userService.getByUsername(username);
        boolean proUser = isProUser(user);
        String userPrompt = request == null ? "" : normalize(request.message(), "");

        String systemPrompt = workflowService.buildSystemPrompt(proUser);
        String gatewayPrompt = workflowService.buildUserPrompt(userPrompt, provider);
        String answer = chatGatewayService.chat(provider, systemPrompt, gatewayPrompt);
        if (answer != null && !answer.isBlank()) {
            return new AgentExecutionResult(provider, answer, proUser);
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
}
