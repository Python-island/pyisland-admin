package com.pyisland.server.agent.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * mihtnelis agent 流式输出服务
 */
@Service
public class MihtnelisAgentStreamService {

    private final ExecutorService streamExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "mihtnelis-stream");
        t.setDaemon(true);
        return t;
    });

    /**
     * 启动 SSE 流
     *
     * @param username 调用用户名
     * @param request  请求参数
     * @return SSE emitter
     */
    public SseEmitter openStream(String username, MihtnelisStreamRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        CompletableFuture.runAsync(() -> emitFlow(emitter, username, request), streamExecutor);
        return emitter;
    }

    private void emitFlow(SseEmitter emitter, String username, MihtnelisStreamRequest request) {
        String userPrompt = request == null || request.message() == null ? "" : request.message().trim();
        String provider = request == null || request.provider() == null || request.provider().isBlank()
                ? "auto"
                : request.provider().trim();

        if (userPrompt.isBlank()) {
            sendEvent(emitter, "error", Map.of(
                    "code", "EMPTY_PROMPT",
                    "message", "message 不能为空"
            ));
            emitter.complete();
            return;
        }

        sendEvent(emitter, "meta", Map.of(
                "agent", "mihtnelis agent",
                "provider", provider,
                "sessionId", normalizeSessionId(request),
                "timestamp", LocalDateTime.now().toString()
        ));

        String answer = "mihtnelis agent 已收到你的请求。当前为 Phase A 流式通道验证输出，"
                + "后续将接入 Spring AI 和 LangChain4j 的真实工具链与计费链路。";

        int billedTokenDelta = 0;
        String[] chunks = answer.split("，");
        for (String chunk : chunks) {
            String safeChunk = chunk == null ? "" : chunk.trim();
            if (safeChunk.isBlank()) {
                continue;
            }
            sendEvent(emitter, "chunk", Map.of("text", safeChunk + "，"));
            billedTokenDelta += estimateTokenDelta(safeChunk);
            sendEvent(emitter, "billing", Map.of(
                    "tokenDelta", estimateTokenDelta(safeChunk),
                    "billedTokenTotal", billedTokenDelta,
                    "billingUnit", "1k_token"
            ));
            sleepSilently(170);
        }

        sendEvent(emitter, "final", Map.of(
                "done", true,
                "billedTokenTotal", billedTokenDelta,
                "billingUnit", "1k_token",
                "agent", "mihtnelis agent",
                "provider", provider,
                "username", Objects.requireNonNullElse(username, "")
        ));
        emitter.complete();
    }

    private String normalizeSessionId(MihtnelisStreamRequest request) {
        if (request == null || request.sessionId() == null || request.sessionId().isBlank()) {
            return "mihtnelis-session";
        }
        return request.sessionId().trim();
    }

    private int estimateTokenDelta(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int charCount = text.length();
        return Math.max(1, (int) Math.ceil(charCount / 1.6));
    }

    private void sendEvent(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException ioException) {
            emitter.completeWithError(ioException);
        }
    }

    private void sleepSilently(long millis) {
        try {
            Thread.sleep(Math.max(0L, millis));
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 流式请求体。
     *
     * @param sessionId 会话 ID。
     * @param message   用户输入。
     * @param provider  指定供应商（可选）。
     */
    public record MihtnelisStreamRequest(String sessionId, String message, String provider) {
    }
}
