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

    public MihtnelisAgentOrchestratorService(AiProviderRouterService providerRouterService,
                                             MihtnelisAgentProperties properties,
                                             UserService userService) {
        this.providerRouterService = providerRouterService;
        this.properties = properties;
        this.userService = userService;
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
        User user = username == null || username.isBlank() ? null : userService.getByUsername(username);
        boolean proUser = isProUser(user);

        StringBuilder content = new StringBuilder();
        content.append("mihtnelis agent 已完成 Phase B 编排路由。")
                .append("当前 provider=").append(provider).append("，")
                .append("接下来将逐步接入 Spring AI 与 LangChain4j 实际执行链路。")
                .append("本轮可用能力：基础对话");

        if (proUser && properties.getOssVector() != null && properties.getOssVector().isEnabled()) {
            content.append("、OSS 向量检索(预留)");
        }
        content.append("。");

        return new AgentExecutionResult(provider, content.toString(), proUser);
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
