package com.pyisland.server.agent.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pyisland.server.agent.service.AgentBalanceRedisService;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AgentRealtimeSttWebSocketHandler extends BinaryWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(AgentRealtimeSttWebSocketHandler.class);

    private static final String STT_MODEL_NAME = "stt-realtime";
    private static final BigDecimal FEN_PER_MINUTE = new BigDecimal("5");
    private static final int SCALE = 8;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentRealtimeSttAuthService agentRealtimeSttAuthService;
    private final TencentRealtimeAsrRelayService tencentRealtimeAsrRelayService;
    private final AgentBalanceRedisService agentBalanceRedisService;
    private final Map<String, SessionState> sessionStateMap = new ConcurrentHashMap<>();

    public AgentRealtimeSttWebSocketHandler(AgentRealtimeSttAuthService agentRealtimeSttAuthService,
                                            TencentRealtimeAsrRelayService tencentRealtimeAsrRelayService,
                                            AgentBalanceRedisService agentBalanceRedisService) {
        this.agentRealtimeSttAuthService = agentRealtimeSttAuthService;
        this.tencentRealtimeAsrRelayService = tencentRealtimeAsrRelayService;
        this.agentBalanceRedisService = agentBalanceRedisService;
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
                safeSendEvent(session, "stt_error", "服务端未配置");
                return;
            }
            if (state.started && state.relaySession != null) {
                safeSendEvent(session, "stt_partial", "语音识别已在运行");
                return;
            }
            if (agentBalanceRedisService.getBalance(state.username).compareTo(FEN_PER_MINUTE) < 0) {
                safeSendEvent(session, "stt_error", "余额不足，请充值后使用");
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
                state.startTimeMillis = System.currentTimeMillis();
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
        log.warn("STT WebSocket transport error, sessionId={}, err={}", session.getId(),
                exception == null ? "unknown" : exception.getMessage());
        SessionState state = sessionStateMap.remove(session.getId());
        if (state != null) {
            stopRelaySession(state);
        }
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.SERVER_ERROR);
            }
        } catch (Exception ignored) {
            // session already broken, nothing to do
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
        if (state.started && state.startTimeMillis > 0) {
            billForUsage(state);
        }
        state.started = false;
        state.startTimeMillis = 0;
    }

    private void billForUsage(SessionState state) {
        long durationMillis = System.currentTimeMillis() - state.startTimeMillis;
        if (durationMillis <= 0) {
            return;
        }
        long durationSeconds = (durationMillis + 999L) / 1000L;
        BigDecimal durationMinutes = BigDecimal.valueOf(durationSeconds)
                .divide(BigDecimal.valueOf(60L), SCALE, RoundingMode.HALF_UP);
        BigDecimal costFen = durationMinutes.multiply(FEN_PER_MINUTE).setScale(SCALE, RoundingMode.HALF_UP);
        if (costFen.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        try {
            AgentBalanceRedisService.DeductResult result = agentBalanceRedisService.deduct(
                    state.username, costFen, STT_MODEL_NAME, 0, 0);
            long durationSec = durationMillis / 1000;
            log.info("STT billing: user={}, duration={}s, cost={} fen, deducted={} fen, balanceZero={}",
                    state.username, durationSec, costFen.toPlainString(),
                    result.actualDeducted().toPlainString(), result.balanceZero());
        } catch (Exception ex) {
            log.error("STT billing failed: user={}, cost={} fen, err={}",
                    state.username, costFen.toPlainString(), ex.getMessage());
        }
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
        private long startTimeMillis;
        private TencentRealtimeAsrRelayService.Session relaySession;

        private SessionState(String username) {
            this.username = username;
            this.started = false;
            this.startTimeMillis = 0;
            this.relaySession = null;
        }
    }
}
