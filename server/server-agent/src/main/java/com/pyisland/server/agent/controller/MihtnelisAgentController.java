package com.pyisland.server.agent.controller;

import com.pyisland.server.agent.service.AgentWebAuthorizationService;
import com.pyisland.server.agent.service.MihtnelisAgentStreamService;
import com.pyisland.server.user.entity.User;
import com.pyisland.server.user.service.UserService;
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
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;

/**
 * mihtnelis agent 用户流式接口。
 */
@RestController
@RequestMapping("/v1/user/ai")
public class MihtnelisAgentController {

    private final MihtnelisAgentStreamService streamService;
    private final AgentWebAuthorizationService webAuthorizationService;
    private final UserService userService;

    public MihtnelisAgentController(MihtnelisAgentStreamService streamService,
                                    AgentWebAuthorizationService webAuthorizationService,
                                    UserService userService) {
        this.streamService = streamService;
        this.webAuthorizationService = webAuthorizationService;
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
        String caller = caller(authentication);
        if (caller == null) {
            return deniedEmitter("UNAUTHORIZED", "未登录");
        }
        User user = userService.getByUsername(caller);
        if (!hasAgentAccess(user)) {
            return deniedEmitter("FORBIDDEN", "仅 Pro 用户可使用 mihtnelis agent");
        }
        SseEmitter emitter = streamService.openStream(caller, resolveClientIp(httpRequest), request);
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

    private boolean hasAgentAccess(User user) {
        if (user == null || user.getRole() == null) {
            return false;
        }
        String role = user.getRole().trim().toLowerCase(Locale.ROOT);
        if (User.ROLE_ADMIN.equals(role)) {
            return true;
        }
        if (!User.ROLE_PRO.equals(role)) {
            return false;
        }
        LocalDateTime expireAt = user.getProExpireAt();
        return expireAt == null || expireAt.isAfter(LocalDateTime.now());
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
}
