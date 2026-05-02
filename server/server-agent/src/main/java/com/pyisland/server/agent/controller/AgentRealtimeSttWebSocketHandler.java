package com.pyisland.server.agent.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pyisland.server.agent.service.AgentRealtimeSttAuthService;
import com.pyisland.server.agent.service.TencentRealtimeAsrRelayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AgentRealtimeSttWebSocketHandler extends BinaryWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(AgentRealtimeSttWebSocketHandler.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentRealtimeSttAuthService agentRealtimeSttAuthService;
    private final TencentRealtimeAsrRelayService tencentRealtimeAsrRelayService;
    private final Map<String, SessionState> sessionStateMap = new ConcurrentHashMap<>();

    public AgentRealtimeSttWebSocketHandler(AgentRealtimeSttAuthService agentRealtimeSttAuthService,
                                            TencentRealtimeAsrRelayService tencentRealtimeAsrRelayService) {
        this.agentRealtimeSttAuthService = agentRealtimeSttAuthService;
        this.tencentRealtimeAsrRelayService = tencentRealtimeAsrRelayService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = resolveQueryParam(session, "token");
        AgentRealtimeSttAuthService.AuthResult authResult = agentRealtimeSttAuthService.authenticate(token);
        if (!authResult.success()) {
            sendEvent(session, "stt_error", authResult.message());
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        sessionStateMap.put(session.getId(), new SessionState(authResult.username()));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        JsonNode payload;
        try {
            payload = objectMapper.readTree(message.getPayload());
        } catch (Exception ex) {
            safeSendEvent(session, "stt_error", "无效的实时语音请求消息");
            return;
        }

        String event = payload.path("event").asText("").trim();
        if (event.isEmpty()) {
            safeSendEvent(session, "stt_error", "缺少 event 字段");
            return;
        }

        SessionState state = sessionStateMap.get(session.getId());
        if (state == null) {
            safeSendEvent(session, "stt_error", "会话已失效");
            return;
        }

        if ("stt_start".equals(event)) {
            if (!tencentRealtimeAsrRelayService.isConfigured()) {
                safeSendEvent(session, "stt_error", "服务端未配置腾讯实时语音识别密钥");
                return;
            }
            if (state.started && state.relaySession != null) {
                safeSendEvent(session, "stt_partial", "语音识别已在运行");
                return;
            }
            try {
                state.relaySession = tencentRealtimeAsrRelayService.startSession(new TencentRealtimeAsrRelayService.Callbacks() {
                    @Override
                    public void onPartial(String text) {
                        safeSendEvent(session, "stt_partial", text);
                    }

                    @Override
                    public void onFinal(String text) {
                        safeSendEvent(session, "stt_final", text);
                    }

                    @Override
                    public void onError(String message) {
                        safeSendEvent(session, "stt_error", message);
                    }
                });
                state.started = true;
                log.info("Realtime STT session started. user={}, sessionId={}", state.username, session.getId());
            } catch (Exception ex) {
                log.warn("Realtime STT start failed. user={}, sessionId={}, message={}", state.username, session.getId(), ex.getMessage());
                safeSendEvent(session, "stt_error", "语音识别启动失败");
                return;
            }
            safeSendEvent(session, "stt_partial", "语音识别连接成功");
            return;
        }

        if ("stt_stop".equals(event)) {
            stopRelaySession(state);
            return;
        }

        safeSendEvent(session, "stt_error", "未知事件类型");
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        SessionState state = sessionStateMap.get(session.getId());
        if (state == null || !state.started || state.relaySession == null) {
            return;
        }
        if (!tencentRealtimeAsrRelayService.isConfigured()) {
            return;
        }
        byte[] bytes = new byte[message.getPayloadLength()];
        message.getPayload().get(bytes);
        try {
            state.relaySession.write(bytes);
        } catch (Exception ex) {
            log.warn("Realtime STT audio relay failed. user={}, sessionId={}, message={}", state.username, session.getId(), ex.getMessage());
            safeSendEvent(session, "stt_error", "语音识别数据上送失败");
            stopRelaySession(state);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        SessionState state = sessionStateMap.remove(session.getId());
        if (state != null) {
            stopRelaySession(state);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        SessionState state = sessionStateMap.remove(session.getId());
        if (state != null) {
            stopRelaySession(state);
        }
        if (session.isOpen()) {
            try {
                sendEvent(session, "stt_error", "实时语音连接异常");
            } finally {
                session.close(CloseStatus.SERVER_ERROR);
            }
        }
    }

    private void sendEvent(WebSocketSession session, String event, String text) throws IOException {
        if (!session.isOpen()) {
            return;
        }
        Map<String, Object> payload = Map.of(
                "event", event,
                "text", text,
                "message", text
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
    }

    private void safeSendEvent(WebSocketSession session, String event, String text) {
        try {
            sendEvent(session, event, text);
        } catch (IOException ignored) {
        }
    }

    private void stopRelaySession(SessionState state) {
        if (state == null) {
            return;
        }
        if (state.relaySession != null) {
            state.relaySession.stop();
            state.relaySession = null;
        }
        state.started = false;
    }

    private String resolveQueryParam(WebSocketSession session, String key) {
        if (session.getUri() == null) {
            return null;
        }
        return UriComponentsBuilder.fromUri(session.getUri())
                .build()
                .getQueryParams()
                .getFirst(key);
    }

    private static final class SessionState {
        private final String username;
        private boolean started;
        private TencentRealtimeAsrRelayService.Session relaySession;

        private SessionState(String username) {
            this.username = username;
            this.started = false;
            this.relaySession = null;
        }
    }
}
