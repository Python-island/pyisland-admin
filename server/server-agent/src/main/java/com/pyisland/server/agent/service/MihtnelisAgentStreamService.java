package com.pyisland.server.agent.service;

import com.pyisland.server.agent.config.MihtnelisAgentProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * mihtnelis agent 流式输出服务
 */
@Service
public class MihtnelisAgentStreamService {

    private static final long ORCHESTRATION_TIMEOUT_SECONDS = 25L;

    private final MihtnelisAgentProperties properties;
    private final MihtnelisAgentOrchestratorService orchestratorService;

    private final ExecutorService streamExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "mihtnelis-stream");
        t.setDaemon(true);
        return t;
    });

    public MihtnelisAgentStreamService(MihtnelisAgentProperties properties,
                                       MihtnelisAgentOrchestratorService orchestratorService) {
        this.properties = properties;
        this.orchestratorService = orchestratorService;
    }

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
        try {
            String userPrompt = request == null || request.message() == null ? "" : request.message().trim();
            String provider = request == null || request.provider() == null || request.provider().isBlank()
                    ? properties.getDefaultProvider()
                    : request.provider().trim();

            if (userPrompt.isBlank()) {
                sendEvent(emitter, "error", Map.of(
                        "code", "EMPTY_PROMPT",
                        "message", "message 不能为空"
                ));
                emitter.complete();
                return;
            }

            int maxInputChars = Math.max(16, properties.getMaxInputChars());
            if (userPrompt.length() > maxInputChars) {
                sendEvent(emitter, "error", Map.of(
                        "code", "INPUT_TOO_LONG",
                        "message", "message 过长",
                        "maxInputChars", maxInputChars
                ));
                emitter.complete();
                return;
            }

            MihtnelisAgentOrchestratorService.AgentExecutionResult executionResult;
            try {
                executionResult = CompletableFuture
                        .supplyAsync(() -> orchestratorService.orchestrate(username, request), streamExecutor)
                        .get(ORCHESTRATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException timeoutException) {
                sendEvent(emitter, "error", Map.of(
                        "code", "MODEL_TIMEOUT",
                        "message", "模型处理超时，请稍后重试"
                ));
                emitter.complete();
                return;
            } catch (ExecutionException executionException) {
                String reason = executionException.getCause() == null
                        ? ""
                        : executionException.getCause().getMessage();
                sendEvent(emitter, "error", Map.of(
                        "code", "MODEL_EXECUTION_ERROR",
                        "message", reason == null || reason.isBlank()
                                ? "模型调用失败，请检查 DeepSeek 配置"
                                : reason
                ));
                emitter.complete();
                return;
            }

            provider = executionResult.provider();

            sendEvent(emitter, "meta", Map.of(
                    "agent", "mihtnelis agent",
                    "provider", provider,
                    "sessionId", normalizeSessionId(request),
                    "timestamp", LocalDateTime.now().toString()
            ));

            String answer = executionResult.answer();

            int billedTokenDelta = 0;
            String[] chunks = answer.split("，");
            for (int i = 0; i < chunks.length; i++) {
                String chunk = chunks[i];
                String safeChunk = chunk == null ? "" : chunk.trim();
                if (safeChunk.isBlank()) {
                    continue;
                }
                boolean hasMoreChunk = false;
                for (int next = i + 1; next < chunks.length; next++) {
                    String nextChunk = chunks[next];
                    if (nextChunk != null && !nextChunk.trim().isBlank()) {
                        hasMoreChunk = true;
                        break;
                    }
                }

                String chunkText = hasMoreChunk ? safeChunk + "，" : safeChunk;
                sendEvent(emitter, "chunk", Map.of("text", chunkText));
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
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            sendEvent(emitter, "error", Map.of(
                    "code", "INTERRUPTED",
                    "message", "请求被中断"
            ));
            emitter.complete();
        } catch (Exception exception) {
            sendEvent(emitter, "error", Map.of(
                    "code", "INTERNAL_ERROR",
                    "message", "服务端处理异常"
            ));
            emitter.completeWithError(exception);
        }
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
