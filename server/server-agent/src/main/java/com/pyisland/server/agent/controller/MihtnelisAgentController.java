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
                         @RequestBody MihtnelisAgentStreamService.MihtnelisStreamRequest request) {
        String caller = caller(authentication);
        if (caller == null) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "未登录"));
        }
        SseEmitter emitter = streamService.openStream(caller, request);
        return emitter;
    }

    private String caller(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return null;
        }
        return authentication.getName();
    }
}
