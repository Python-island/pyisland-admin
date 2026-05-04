package com.pyisland.server.agent.controller;

import com.pyisland.server.agent.service.AgentWebAuthorizationService;
import com.pyisland.server.agent.service.AgentLocalToolRelayService;
import com.pyisland.server.agent.service.LangChainWorkflowService;
import com.pyisland.server.agent.service.MihtnelisAgentStreamService;
import com.pyisland.server.user.entity.User;
import com.pyisland.server.user.service.UserService;
import com.pyisland.server.servicestatus.entity.ServiceStatus;
import com.pyisland.server.servicestatus.service.ServiceStatusService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * mihtnelis agent 用户流式接口。
 */
@RestController
@RequestMapping("/v1/user/ai")
public class MihtnelisAgentController {

    private final MihtnelisAgentStreamService streamService;
    private final AgentWebAuthorizationService webAuthorizationService;
    private final AgentLocalToolRelayService localToolRelayService;
    private final ServiceStatusService serviceStatusService;
    private final LangChainWorkflowService workflowService;
    private final UserService userService;

    public MihtnelisAgentController(MihtnelisAgentStreamService streamService,
                                    AgentWebAuthorizationService webAuthorizationService,
                                    AgentLocalToolRelayService localToolRelayService,
                                    ServiceStatusService serviceStatusService,
                                    LangChainWorkflowService workflowService,
                                    UserService userService) {
        this.streamService = streamService;
        this.webAuthorizationService = webAuthorizationService;
        this.localToolRelayService = localToolRelayService;
        this.serviceStatusService = serviceStatusService;
        this.workflowService = workflowService;
        this.userService = userService;
    }

    /**
     * Agent 流式会话入口。
     *
     * 接口名不做强约束，此处挂载到 /v1/user/ai/agent/stream。
     *
     * @param authentication 当前认证信息。
     * @param request        请求体。
     * @return SSE 输出流。
     */
    @PostMapping(value = "/agent/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Object stream(Authentication authentication,
                         HttpServletRequest httpRequest,
                         @RequestBody MihtnelisAgentStreamService.MihtnelisStreamRequest request) {
        ServiceStatus agentStatus = serviceStatusService.getByApiName(AgentAdminController.AGENT_SERVICE_API_NAME);
        if (agentStatus != null && Boolean.FALSE.equals(agentStatus.getStatus())) {
            String reason = agentStatus.getMessage() != null && !agentStatus.getMessage().isBlank()
                    ? agentStatus.getMessage()
                    : "Agent 服务已暂停，请稍后再试";
            return deniedEmitter("SERVICE_UNAVAILABLE", reason);
        }
        String caller = caller(authentication);
        if (caller == null) {
            return deniedEmitter("UNAUTHORIZED", "未登录");
        }
        SseEmitter emitter = streamService.openStream(
                caller,
                resolveClientIp(httpRequest),
                resolveTraceId(httpRequest),
                request
        );
        return emitter;
    }

    @PostMapping(value = "/agent/web-access/resolve", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> resolveWebAccess(Authentication authentication,
                                              @RequestBody AgentWebAccessResolveRequest request) {
        String caller = caller(authentication);
        if (caller == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "error", "未登录"
            ));
        }
        AgentWebAuthorizationService.ResolveResult result = webAuthorizationService.resolve(
                caller,
                request == null ? "" : request.requestId(),
                request != null && request.allow()
        );
        if (!result.success()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", result.error(),
                    "data", result.data()
            ));
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", result.data()
        ));
    }

    @PostMapping(value = "/agent/local-tool/resolve", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> resolveLocalToolAccess(Authentication authentication,
                                                    @RequestBody AgentLocalToolAccessResolveRequest request) {
        String caller = caller(authentication);
        if (caller == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "error", "未登录"
            ));
        }
        AgentLocalToolRelayService.ResolveAuthorizationResult result = localToolRelayService.resolveAuthorization(
                caller,
                request == null ? "" : request.requestId(),
                request != null && request.allow()
        );
        if (!result.success()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", result.error(),
                    "data", result.data()
            ));
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", result.data()
        ));
    }

    @PostMapping(value = "/agent/tool-result", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> resolveLocalToolResult(Authentication authentication,
                                                    @RequestBody AgentLocalToolResolveRequest request) {
        String caller = caller(authentication);
        if (caller == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "error", "未登录"
            ));
        }
        AgentLocalToolRelayService.ResolveResult result = localToolRelayService.resolve(
                caller,
                request == null ? "" : request.requestId(),
                request != null && request.success(),
                request == null ? Map.of() : request.result(),
                request == null ? "" : request.error(),
                request == null ? 0L : request.durationMs()
        );
        if (!result.success()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", result.error(),
                    "data", result.data()
            ));
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", result.data()
        ));
    }

    private SseEmitter deniedEmitter(String code, String message) {
        SseEmitter emitter = new SseEmitter(0L);
        try {
            emitter.send(SseEmitter.event().name("error").data(new AgentErrorPayload(code, message)));
            emitter.send(SseEmitter.event().name("final").data(new AgentFinalPayload(true)));
            emitter.complete();
        } catch (IOException ioException) {
            emitter.completeWithError(ioException);
        }
        return emitter;
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String forwarded = firstIp(request.getHeader("X-Forwarded-For"));
        if (!forwarded.isBlank()) {
            return forwarded;
        }
        String realIp = firstIp(request.getHeader("X-Real-IP"));
        if (!realIp.isBlank()) {
            return realIp;
        }
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr == null ? "" : remoteAddr.trim();
    }

    private String firstIp(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String[] values = raw.split(",");
        return values.length == 0 ? "" : values[0].trim();
    }

    private String resolveTraceId(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String[] candidates = {"X-Trace-Id", "Trace-Id", "X-Request-Id", "Request-Id"};
        for (String header : candidates) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String caller(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return null;
        }
        return authentication.getName();
    }

    private record AgentErrorPayload(String code, String message) {
    }

    private record AgentFinalPayload(boolean done) {
    }

    private record AgentWebAccessResolveRequest(String requestId, boolean allow) {
    }

    private record AgentLocalToolResolveRequest(String requestId,
                                                boolean success,
                                                Object result,
                                                String error,
                                                Long durationMs) {
    }

    private record AgentLocalToolAccessResolveRequest(String requestId,
                                                      boolean allow) {
    }

    /**
     * 获取 Agent 系统提示词
     */
    @PostMapping(value = "/agent/prompt", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getAgentPrompt(Authentication authentication,
                                             @RequestBody AgentPromptRequest request) {
        String username = caller(authentication);
        if (username == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "error", "未登录"
            ));
        }
        String agentMode = request == null || request.agentMode() == null
                ? "mihtnelis" : request.agentMode().trim();
        boolean snapshotMode = request != null && Boolean.TRUE.equals(request.snapshotMode());
        boolean localMode = request != null && Boolean.TRUE.equals(request.localMode());
        List<String> workspaces = request == null ? null : request.workspaces();
        List<MihtnelisAgentStreamService.SkillEntry> skills = request == null ? null : request.skills();

        User user = userService.getByUsername(username);
        boolean proUser = isProUser(user);

        String systemPrompt = workflowService.buildSystemPrompt(
                agentMode, proUser, workspaces, skills, snapshotMode, localMode);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "systemPrompt", systemPrompt
        ));
    }

    private boolean isProUser(User user) {
        if (user == null || user.getRole() == null) {
            return false;
        }
        String role = user.getRole().trim().toLowerCase(java.util.Locale.ROOT);
        return User.ROLE_PRO.equals(role) || User.ROLE_ADMIN.equals(role);
    }

    private record AgentPromptRequest(String agentMode,
                                      Boolean snapshotMode,
                                      Boolean localMode,
                                      List<String> workspaces,
                                      List<MihtnelisAgentStreamService.SkillEntry> skills) {
    }
}
