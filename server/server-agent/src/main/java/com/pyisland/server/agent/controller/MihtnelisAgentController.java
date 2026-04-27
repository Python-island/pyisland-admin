package com.pyisland.server.agent.controller;

import com.pyisland.server.agent.service.MihtnelisAgentStreamService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

/**
 * mihtnelis agent 用户流式接口。
 */
@RestController
@RequestMapping("/v1/user/ai")
public class MihtnelisAgentController {

    private final MihtnelisAgentStreamService streamService;

    public MihtnelisAgentController(MihtnelisAgentStreamService streamService) {
        this.streamService = streamService;
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
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "未登录"));
        }
        SseEmitter emitter = streamService.openStream(caller, resolveClientIp(httpRequest), request);
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

    private String caller(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return null;
        }
        return authentication.getName();
    }
}
