package com.pyisland.server.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pyisland.server.agent.config.MihtnelisAgentProperties;
import com.pyisland.server.agent.utils.AgentStreamChunkUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * mihtnelis agent 流式输出服务
 */
@Service
public class MihtnelisAgentStreamService {

    private static final long ORCHESTRATION_TIMEOUT_SECONDS = 25L;
    private static final long WEB_ACCESS_WAIT_TIMEOUT_SECONDS = 120L;
    private static final long LOCAL_TOOL_WAIT_TIMEOUT_SECONDS = 120L;
    private static final int MAX_CONTEXT_CHARS = 1_000_000;
    private static final Pattern THINK_TAG_PATTERN = Pattern.compile("<think>(.*?)</think>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final MihtnelisAgentProperties properties;
    private final MihtnelisAgentOrchestratorService orchestratorService;
    private final AgentWebAuthorizationService webAuthorizationService;
    private final AgentLocalToolRelayService localToolRelayService;

    private final ExecutorService streamExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "mihtnelis-stream");
        t.setDaemon(true);
        return t;
    });

    public MihtnelisAgentStreamService(MihtnelisAgentProperties properties,
                                       MihtnelisAgentOrchestratorService orchestratorService,
                                       AgentWebAuthorizationService webAuthorizationService,
                                       AgentLocalToolRelayService localToolRelayService) {
        this.properties = properties;
        this.orchestratorService = orchestratorService;
        this.webAuthorizationService = webAuthorizationService;
        this.localToolRelayService = localToolRelayService;
    }

    /**
     * 启动 SSE 流
     *
     * @param username 调用用户名
     * @param request  请求参数
     * @return SSE emitter
     */
    public SseEmitter openStream(String username, String clientIp, MihtnelisStreamRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        CompletableFuture.runAsync(() -> emitFlow(emitter, username, clientIp, request), streamExecutor);
        return emitter;
    }

    private void emitFlow(SseEmitter emitter, String username, String clientIp, MihtnelisStreamRequest request) {
        try {
            String userPrompt = request == null || request.message() == null ? "" : request.message().trim();
            String context = normalizeContext(request == null ? null : request.context());
            String provider = request == null || request.provider() == null || request.provider().isBlank()
                    ? properties.getDefaultProvider()
                    : request.provider().trim();
            boolean thinkingEnabled = resolveThinkingEnabled(request);
            String reasoningEffort = resolveReasoningEffort(request);
            MihtnelisStreamRequest effectiveRequest = new MihtnelisStreamRequest(
                    normalizeSessionId(request),
                    userPrompt,
                    provider,
                    context,
                    request == null ? null : request.thinking(),
                    request == null ? null : request.reasoningEffort()
            );

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

            AtomicInteger toolTurnCounter = new AtomicInteger(0);
            AtomicInteger thinkTurnCounter = new AtomicInteger(0);
            Deque<Integer> pendingToolTurns = new ArrayDeque<>();
            AgentToolExecutionService.ToolExecutionObserver toolExecutionObserver =
                    new AgentToolExecutionService.ToolExecutionObserver() {
                        @Override
                        public void onToolCallRequested(String toolName, Map<String, Object> arguments) {
                            if (isClientLocalTool(toolName)) {
                                return;
                            }
                            int turn = toolTurnCounter.incrementAndGet();
                            pendingToolTurns.addLast(turn);
                            sendEvent(emitter, "tool_call_request", Map.of(
                                    "requestId", "",
                                    "turn", turn,
                                    "tool", toolName,
                                    "arguments", arguments == null ? Map.of() : arguments,
                                    "riskLevel", "server"
                            ));
                        }

                        @Override
                        public void onToolCallCompleted(String toolName,
                                                        Map<String, Object> arguments,
                                                        AgentToolExecutionService.ToolResult result) {
                            if (isClientLocalTool(toolName) || isPendingLocalToolResult(result)) {
                                return;
                            }
                            int turn = pendingToolTurns.isEmpty()
                                    ? toolTurnCounter.incrementAndGet()
                                    : pendingToolTurns.removeFirst();
                            sendEvent(emitter, "tool_call_result", Map.of(
                                    "requestId", "",
                                    "turn", turn,
                                    "tool", result == null ? toolName : result.tool(),
                                    "success", result != null && result.success(),
                                    "error", result == null || result.error() == null ? "" : result.error(),
                                    "result", result == null || result.data() == null ? Map.of() : result.data(),
                                    "durationMs", 0
                            ));
                        }

                        @Override
                        public void onThinking(int turn, String content) {
                            String safeContent = content == null ? "" : content.trim();
                            if (safeContent.isBlank()) {
                                return;
                            }
                            int safeTurn = turn > 0 ? turn : thinkTurnCounter.incrementAndGet();
                            thinkTurnCounter.set(Math.max(thinkTurnCounter.get(), safeTurn));
                            List<String> thinkChunks = AgentStreamChunkUtils.splitForStreaming(safeContent);
                            for (int chunkIndex = 0; chunkIndex < thinkChunks.size(); chunkIndex++) {
                                String thinkChunk = thinkChunks.get(chunkIndex);
                                String safeChunk = thinkChunk == null ? "" : thinkChunk;
                                if (safeChunk.isBlank()) {
                                    continue;
                                }
                                boolean done = chunkIndex == thinkChunks.size() - 1;
                                sendEvent(emitter, "think", Map.of(
                                        "text", safeChunk,
                                        "index", Math.max(0, safeTurn - 1),
                                        "done", done
                                ));
                                sleepSilently(90);
                            }
                        }
                    };

            MihtnelisAgentOrchestratorService.AgentExecutionResult executionResult;
            boolean metaSent = false;
            while (true) {
                executionResult = runOrchestration(username, clientIp, effectiveRequest, toolExecutionObserver);
                provider = executionResult.provider();
                if (!metaSent) {
                    sendEvent(emitter, "meta", Map.of(
                            "agent", "mihtnelis agent",
                            "provider", provider,
                            "sessionId", effectiveRequest.sessionId(),
                            "thinkingEnabled", thinkingEnabled,
                            "reasoningEffort", reasoningEffort,
                            "timestamp", LocalDateTime.now().toString()
                    ));
                    metaSent = true;
                }

                if (executionResult.pausedForWebAccess() && executionResult.pendingWebAccess() != null) {
                    MihtnelisAgentOrchestratorService.PendingWebAccess pendingWebAccess = executionResult.pendingWebAccess();
                    sendEvent(emitter, "web_access_request", Map.of(
                            "requestId", pendingWebAccess.requestId(),
                            "url", pendingWebAccess.url(),
                            "message", "该 URL 需要授权后才可继续读取"
                    ));
                    AgentWebAuthorizationService.AwaitResult awaitResult = webAuthorizationService.awaitDecision(
                            username,
                            pendingWebAccess.requestId(),
                            WEB_ACCESS_WAIT_TIMEOUT_SECONDS
                    );
                    if (!awaitResult.resolved()) {
                        sendEvent(emitter, "error", Map.of(
                                "code", "WEB_ACCESS_TIMEOUT",
                                "message", awaitResult.error().isBlank()
                                        ? "网页访问授权等待超时"
                                        : awaitResult.error()
                        ));
                        emitter.complete();
                        return;
                    }
                    if (!awaitResult.allowed()) {
                        sendEvent(emitter, "error", Map.of(
                                "code", "WEB_ACCESS_DENIED",
                                "message", "用户拒绝访问该 URL"
                        ));
                        emitter.complete();
                        return;
                    }
                    sendEvent(emitter, "web_access_resolved", Map.of(
                            "requestId", pendingWebAccess.requestId(),
                            "url", pendingWebAccess.url(),
                            "allowed", true
                    ));
                    continue;
                }
                if (executionResult.pausedForLocalTool() && executionResult.pendingLocalTool() != null) {
                    MihtnelisAgentOrchestratorService.PendingLocalTool pendingLocalTool = executionResult.pendingLocalTool();
                    sendEvent(emitter, "tool_call_request", Map.of(
                            "requestId", pendingLocalTool.requestId(),
                            "tool", pendingLocalTool.tool(),
                            "arguments", pendingLocalTool.arguments(),
                            "argumentsDigest", pendingLocalTool.argumentsDigest(),
                            "riskLevel", pendingLocalTool.riskLevel()
                    ));
                    AgentLocalToolRelayService.AwaitResult awaitResult = localToolRelayService.awaitResult(
                            username,
                            pendingLocalTool.requestId(),
                            LOCAL_TOOL_WAIT_TIMEOUT_SECONDS
                    );
                    if (!awaitResult.resolved() || awaitResult.payload() == null) {
                        sendEvent(emitter, "error", Map.of(
                                "code", "LOCAL_TOOL_WAIT_TIMEOUT",
                                "message", awaitResult.error().isBlank()
                                        ? "本地工具执行等待超时"
                                        : awaitResult.error()
                        ));
                        emitter.complete();
                        return;
                    }
                    AgentLocalToolRelayService.LocalToolExecutionPayload payload = awaitResult.payload();
                    sendEvent(emitter, "tool_call_result", Map.of(
                            "requestId", payload.requestId(),
                            "tool", payload.tool(),
                            "success", payload.success(),
                            "error", payload.error(),
                            "result", payload.result() == null ? Map.of() : payload.result(),
                            "durationMs", payload.durationMs()
                    ));
                    continue;
                }
                break;
            }

            String rawAnswer = unwrapFinalEnvelope(executionResult.answer());
            String visibleAnswer = rawAnswer;
            if (thinkingEnabled && thinkTurnCounter.get() == 0) {
                List<String> thinkBlocks = extractThinkBlocks(rawAnswer);
                for (int thinkIndex = 0; thinkIndex < thinkBlocks.size(); thinkIndex++) {
                    String think = thinkBlocks.get(thinkIndex);
                    String safeThink = think == null ? "" : think.trim();
                    if (safeThink.isBlank()) {
                        continue;
                    }
                    List<String> thinkChunks = AgentStreamChunkUtils.splitForStreaming(safeThink);
                    for (int chunkIndex = 0; chunkIndex < thinkChunks.size(); chunkIndex++) {
                        String thinkChunk = thinkChunks.get(chunkIndex);
                        String safeChunk = thinkChunk == null ? "" : thinkChunk;
                        if (safeChunk.isBlank()) {
                            continue;
                        }
                        boolean done = chunkIndex == thinkChunks.size() - 1;
                        sendEvent(emitter, "think", Map.of(
                                "text", safeChunk,
                                "index", thinkIndex,
                                "done", done
                        ));
                        sleepSilently(90);
                    }
                }
                visibleAnswer = stripThinkBlocks(rawAnswer);
            }

            int billedTokenDelta = 0;
            List<String> chunks = AgentStreamChunkUtils.splitForStreaming(visibleAnswer);
            for (String chunk : chunks) {
                String safeChunk = chunk == null ? "" : chunk;
                if (safeChunk.isBlank()) {
                    continue;
                }
                sendEvent(emitter, "chunk", Map.of("text", safeChunk));
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
        } catch (TimeoutException timeoutException) {
            sendEvent(emitter, "error", Map.of(
                    "code", "MODEL_TIMEOUT",
                    "message", "模型处理超时，请稍后重试"
            ));
            emitter.complete();
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

    private MihtnelisAgentOrchestratorService.AgentExecutionResult runOrchestration(String username,
                                                                                     String clientIp,
                                                                                     MihtnelisStreamRequest request,
                                                                                     AgentToolExecutionService.ToolExecutionObserver toolExecutionObserver)
            throws TimeoutException, ExecutionException, InterruptedException {
        return CompletableFuture
                .supplyAsync(() -> orchestratorService.orchestrate(username, clientIp, request, toolExecutionObserver), streamExecutor)
                .get(ORCHESTRATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private boolean isClientLocalTool(String toolName) {
        String safeToolName = toolName == null ? "" : toolName.trim().toLowerCase();
        return safeToolName.startsWith("file.")
                || safeToolName.startsWith("cmd.")
                || "web.search".equals(safeToolName);
    }

    private boolean isPendingLocalToolResult(AgentToolExecutionService.ToolResult toolResult) {
        if (toolResult == null || !toolResult.success()) {
            return false;
        }
        if (!(toolResult.data() instanceof Map<?, ?> map)) {
            return false;
        }
        Object required = map.get("localToolExecutionRequired");
        if (required instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return "true".equalsIgnoreCase(String.valueOf(required));
    }

    private String normalizeSessionId(MihtnelisStreamRequest request) {
        if (request == null || request.sessionId() == null || request.sessionId().isBlank()) {
            return "mihtnelis-session";
        }
        return request.sessionId().trim();
    }

    private String normalizeContext(String context) {
        String safeContext = context == null ? "" : context;
        if (safeContext.length() <= MAX_CONTEXT_CHARS) {
            return safeContext;
        }
        return safeContext.substring(safeContext.length() - MAX_CONTEXT_CHARS);
    }

    private boolean resolveThinkingEnabled(MihtnelisStreamRequest request) {
        MihtnelisAgentProperties.Provider deepseek = properties.getLlm() == null ? null : properties.getLlm().getDeepseek();
        boolean defaultThinking = deepseek != null && deepseek.isThinking();
        if (request == null || request.thinking() == null) {
            return defaultThinking;
        }
        return request.thinking();
    }

    private String resolveReasoningEffort(MihtnelisStreamRequest request) {
        MihtnelisAgentProperties.Provider deepseek = properties.getLlm() == null ? null : properties.getLlm().getDeepseek();
        String defaultEffort = deepseek == null ? "medium" : normalizeReasoningEffort(deepseek.getReasoningEffort());
        if (request == null || request.reasoningEffort() == null || request.reasoningEffort().isBlank()) {
            return defaultEffort;
        }
        return normalizeReasoningEffort(request.reasoningEffort());
    }

    private String normalizeReasoningEffort(String source) {
        String candidate = source == null ? "" : source.trim().toLowerCase();
        if ("low".equals(candidate) || "high".equals(candidate)) {
            return candidate;
        }
        return "medium";
    }

    private List<String> extractThinkBlocks(String answer) {
        String source = answer == null ? "" : answer;
        Matcher matcher = THINK_TAG_PATTERN.matcher(source);
        List<String> blocks = new java.util.ArrayList<>();
        while (matcher.find()) {
            blocks.add(matcher.group(1));
        }
        return blocks;
    }

    private String stripThinkBlocks(String answer) {
        String source = answer == null ? "" : answer;
        String cleaned = THINK_TAG_PATTERN.matcher(source).replaceAll("");
        return cleaned.trim();
    }

    private String unwrapFinalEnvelope(String answer) {
        String source = answer == null ? "" : answer.trim();
        if (source.isBlank() || !source.startsWith("{")) {
            return source;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(source);
            String type = root.path("type").asText("").trim();
            if (!"final".equalsIgnoreCase(type)) {
                return source;
            }
            String finalAnswer = root.path("answer").asText("").trim();
            return finalAnswer.isBlank() ? source : finalAnswer;
        } catch (Exception ignored) {
            return source;
        }
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
    public record MihtnelisStreamRequest(String sessionId,
                                         String message,
                                         String provider,
                                         String context,
                                         Boolean thinking,
                                         String reasoningEffort) {
    }
}
