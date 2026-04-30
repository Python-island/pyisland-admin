package com.pyisland.server.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pyisland.server.agent.config.MihtnelisAgentProperties;
import com.pyisland.server.agent.utils.AgentJsonUtils;
import com.pyisland.server.agent.utils.AgentStreamChunkUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
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

    private static final long ORCHESTRATION_TIMEOUT_SECONDS = 120L;
    private static final long WEB_ACCESS_WAIT_TIMEOUT_SECONDS = 120L;
    private static final long LOCAL_TOOL_WAIT_TIMEOUT_SECONDS = 120L;
    private static final int MAX_CONTEXT_CHARS = 1_000_000;
    private static final Pattern THINK_TAG_PATTERN = Pattern.compile("<think>(.*?)</think>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ATTACHMENT_TAG_PATTERN = Pattern.compile("<attachment name=\"[^\"]*\">\\n[\\s\\S]*?\\n</attachment>", Pattern.DOTALL);
    private static final int MAX_TOTAL_MESSAGE_CHARS = 600_000;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final MihtnelisAgentProperties properties;
    private final MihtnelisAgentOrchestratorService orchestratorService;
    private final AgentWebAuthorizationService webAuthorizationService;
    private final AgentLocalToolRelayService localToolRelayService;
    private final AgentModelPricingService modelPricingService;
    private final AgentBalanceRedisService balanceRedisService;

    private final ExecutorService streamExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "mihtnelis-stream");
        t.setDaemon(true);
        return t;
    });

    public MihtnelisAgentStreamService(MihtnelisAgentProperties properties,
                                       MihtnelisAgentOrchestratorService orchestratorService,
                                       AgentWebAuthorizationService webAuthorizationService,
                                       AgentLocalToolRelayService localToolRelayService,
                                       AgentModelPricingService modelPricingService,
                                       AgentBalanceRedisService balanceRedisService) {
        this.properties = properties;
        this.orchestratorService = orchestratorService;
        this.webAuthorizationService = webAuthorizationService;
        this.localToolRelayService = localToolRelayService;
        this.modelPricingService = modelPricingService;
        this.balanceRedisService = balanceRedisService;
    }

    /**
     * 启动 SSE 流
     *
     * @param username 调用用户名
     * @param request  请求参数
     * @return SSE emitter
     */
    public SseEmitter openStream(String username, String clientIp, String traceId, MihtnelisStreamRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        CompletableFuture.runAsync(() -> emitFlow(emitter, username, clientIp, traceId, request), streamExecutor);
        return emitter;
    }

    private void emitFlow(SseEmitter emitter, String username, String clientIp, String traceId, MihtnelisStreamRequest request) {
        try {
            String userPrompt = request == null || request.message() == null ? "" : request.message().trim();
            String context = normalizeContext(request == null ? null : request.context());
            String provider = request == null || request.provider() == null || request.provider().isBlank()
                    ? properties.getDefaultProvider()
                    : request.provider().trim();
            boolean thinkingEnabled = resolveThinkingEnabled(request);
            String reasoningEffort = resolveReasoningEffort(request);
            String model = request == null || request.model() == null ? null : request.model().trim();
            MihtnelisStreamRequest effectiveRequest = new MihtnelisStreamRequest(
                    normalizeSessionId(request),
                    userPrompt,
                    provider,
                    model,
                    context,
                    request == null ? null : request.workspaces(),
                    request == null ? null : request.skills(),
                    request == null ? null : request.thinking(),
                    request == null ? null : request.reasoningEffort()
            );
            String effectiveTraceId = normalizeTraceId(traceId);
            String effectiveSessionId = effectiveRequest.sessionId() == null ? "" : effectiveRequest.sessionId();

            if (userPrompt.isBlank()) {
                sendEvent(emitter, "error", Map.of(
                        "code", "EMPTY_PROMPT",
                        "message", "message 不能为空"
                ));
                emitter.complete();
                return;
            }

            if (userPrompt.length() > MAX_TOTAL_MESSAGE_CHARS) {
                sendEvent(emitter, "error", Map.of(
                        "code", "INPUT_TOO_LONG",
                        "message", "message 过长（含附件）",
                        "maxInputChars", MAX_TOTAL_MESSAGE_CHARS
                ));
                emitter.complete();
                return;
            }
            String questionOnly = ATTACHMENT_TAG_PATTERN.matcher(userPrompt).replaceAll("").trim();
            int maxInputChars = Math.max(16, properties.getMaxInputChars());
            if (questionOnly.length() > maxInputChars) {
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
            java.util.concurrent.atomic.AtomicBoolean contentStreamedFlag = new java.util.concurrent.atomic.AtomicBoolean(false);
            java.util.concurrent.atomic.AtomicBoolean thinkingBlockDone = new java.util.concurrent.atomic.AtomicBoolean(false);
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
                                    "purpose", "",
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
                            // 每次 onThinking 调用都视为一个新的思考块，避免跨编排迭代续写到旧块。
                            int safeTurn = thinkTurnCounter.incrementAndGet();
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

                        @Override
                        public void onThinkingDelta(String deltaText, boolean done) {
                            String safeDelta = deltaText == null ? "" : deltaText;
                            if (safeDelta.isEmpty() && !done) {
                                return;
                            }
                            // 新一轮非空 delta 到来时，如果上一个块已结束或首次，递增计数器创建新块
                            if (!safeDelta.isEmpty() && (thinkTurnCounter.get() == 0 || thinkingBlockDone.compareAndSet(true, false))) {
                                thinkTurnCounter.incrementAndGet();
                            }
                            int currentIndex = thinkTurnCounter.get();
                            if (currentIndex == 0) {
                                currentIndex = thinkTurnCounter.incrementAndGet();
                            }
                            sendEvent(emitter, "think", Map.of(
                                    "text", safeDelta,
                                    "index", Math.max(0, currentIndex - 1),
                                    "done", done
                            ));
                            if (done) {
                                thinkingBlockDone.set(true);
                            }
                        }

                        @Override
                        public void onContentDelta(String deltaText, boolean done) {
                            String safeDelta = deltaText == null ? "" : deltaText;
                            if (safeDelta.isEmpty() && !done) {
                                return;
                            }
                            if (!safeDelta.isEmpty()) {
                                contentStreamedFlag.set(true);
                                sendEvent(emitter, "chunk", Map.of("text", safeDelta));
                            }
                        }

                        @Override
                        public void onContentReset() {
                            if (contentStreamedFlag.getAndSet(false)) {
                                sendEvent(emitter, "chunk_reset", Map.of());
                            }
                        }

                        @Override
                        public void onTodoUpdate(List<Map<String, Object>> items) {
                            if (items == null) {
                                return;
                            }
                            int turn = toolTurnCounter.incrementAndGet();
                            sendEvent(emitter, "todo", Map.of(
                                    "turn", turn,
                                    "items", items,
                                    "count", items.size()
                            ));
                        }
                    };

            // 余额预检：余额为 0 直接拒绝
            if (balanceRedisService.isBalanceZero(username)) {
                sendEvent(emitter, "error", Map.of(
                        "code", "BALANCE_ZERO",
                        "message", "余额不足，请充值后再使用 AI 助手"
                ));
                emitter.complete();
                return;
            }

            // 立即发送 meta 事件，让客户端获得即时反馈
            Map<String, Object> metaPayload = new LinkedHashMap<>();
            metaPayload.put("agent", "mihtnelis agent");
            metaPayload.put("provider", provider);
            metaPayload.put("sessionId", effectiveSessionId);
            metaPayload.put("contextId", effectiveSessionId);
            metaPayload.put("traceId", effectiveTraceId);
            metaPayload.put("thinkingEnabled", thinkingEnabled);
            metaPayload.put("reasoningEffort", reasoningEffort);
            metaPayload.put("timestamp", LocalDateTime.now().toString());
            sendEvent(emitter, "meta", metaPayload);
            sendEvent(emitter, "status", Map.of(
                    "phase", "orchestrating",
                    "message", thinkingEnabled ? "正在深度思考中…" : "正在处理中…"
            ));

            MihtnelisAgentOrchestratorService.AgentExecutionResult executionResult;
            String accumulatedScratchpad = "";
            int nextStartTurn = 1;
            while (true) {
                executionResult = runOrchestration(username, clientIp, effectiveRequest, toolExecutionObserver, accumulatedScratchpad, nextStartTurn);
                provider = executionResult.provider();

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
                    boolean authorizationRequired = requiresLocalToolAuthorization(pendingLocalTool);
                    String localToolPurpose = normalizeToolPurpose(pendingLocalTool.tool(), pendingLocalTool.purpose());
                    // 本地工具在观察者侧被跳过，主循环负责自增 turn，
                    // 保证与思考块的时间线对齐。
                    int localToolTurn = toolTurnCounter.incrementAndGet();
                    sendEvent(emitter, "tool_call_request", Map.of(
                            "requestId", pendingLocalTool.requestId(),
                            "turn", localToolTurn,
                            "tool", pendingLocalTool.tool(),
                            "arguments", pendingLocalTool.arguments(),
                            "argumentsDigest", pendingLocalTool.argumentsDigest(),
                            "purpose", localToolPurpose,
                            "riskLevel", pendingLocalTool.riskLevel(),
                            "authorizationRequired", authorizationRequired,
                            "message", authorizationRequired
                                    ? buildLocalToolAuthorizationMessage(pendingLocalTool.tool(), localToolPurpose)
                                    : ""
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
                            "turn", localToolTurn,
                            "tool", payload.tool(),
                            "success", payload.success(),
                            "error", payload.error(),
                            "result", payload.result() == null ? Map.of() : payload.result(),
                            "durationMs", payload.durationMs()
                    ));
                    accumulatedScratchpad = buildLocalToolObservation(
                            executionResult.resumeScratchpad(),
                            executionResult.resumeTurn(),
                            pendingLocalTool.tool(),
                            pendingLocalTool.arguments(),
                            payload
                    );
                    nextStartTurn = executionResult.resumeTurn() + 1;
                    continue;
                }
                break;
            }

            String rawAnswer = unwrapFinalEnvelope(executionResult.answer());
            String visibleAnswer = rawAnswer;
            if (thinkingEnabled) {
                // 仅当未通过流式 onThinkingDelta 推送过思考内容时，才从最终响应中提取并推送
                if (thinkTurnCounter.get() == 0) {
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
                }
                // 无论是否流式推送，都需要从可见回答中移除 <think> 标签
                visibleAnswer = stripThinkBlocks(rawAnswer);
            }

            int streamedChunkTokens = 0;
            // 如果 content 已通过 onContentDelta 实时推送，跳过重复的分块发送
            if (!contentStreamedFlag.get()) {
                List<String> chunks = AgentStreamChunkUtils.splitForStreaming(visibleAnswer);
                for (String chunk : chunks) {
                    String safeChunk = chunk == null ? "" : chunk;
                    if (safeChunk.isBlank()) {
                        continue;
                    }
                    sendEvent(emitter, "chunk", Map.of("text", safeChunk));
                    int chunkTokenEst = estimateTokenDelta(safeChunk);
                    streamedChunkTokens += chunkTokenEst;
                    sendEvent(emitter, "billing", Map.of(
                            "tokenDelta", chunkTokenEst,
                            "billedTokenTotal", streamedChunkTokens,
                            "billingUnit", "1k_token"
                    ));
                    sleepSilently(170);
                }
            }

            // 优先使用 LLM API 返回的真实 token 用量；未获取到时退回字符估算
            int apiPromptTokens = executionResult.totalPromptTokens();
            int apiCompletionTokens = executionResult.totalCompletionTokens();
            int inputTokens = apiPromptTokens > 0 ? apiPromptTokens
                    : (estimateTokenDelta(userPrompt) + estimateTokenDelta(context));
            int outputTokens = apiCompletionTokens > 0 ? apiCompletionTokens
                    : streamedChunkTokens;
            int reasoningTokens = executionResult.totalReasoningTokens();
            String billingModel = model != null && !model.isBlank() ? model : "deepseek-v4-flash";
            java.math.BigDecimal deductedFen = java.math.BigDecimal.ZERO;
            try {
                deductedFen = modelPricingService.deductForUsage(username, billingModel, inputTokens, outputTokens);
            } catch (Exception billingEx) {
                // 扣费失败不阻断流式响应
            }

            Map<String, Object> finalPayload = new LinkedHashMap<>();
            finalPayload.put("done", true);
            finalPayload.put("billedTokenTotal", inputTokens + outputTokens);
            finalPayload.put("billedInputTokens", inputTokens);
            finalPayload.put("billedOutputTokens", outputTokens);
            finalPayload.put("billedReasoningTokens", reasoningTokens);
            finalPayload.put("billedModel", billingModel);
            finalPayload.put("deductedFen", deductedFen);
            finalPayload.put("billingUnit", "1M_token");
            finalPayload.put("tokenSource", apiPromptTokens > 0 ? "api" : "estimate");
            finalPayload.put("agent", "mihtnelis agent");
            finalPayload.put("provider", provider);
            finalPayload.put("username", Objects.requireNonNullElse(username, ""));
            finalPayload.put("sessionId", effectiveSessionId);
            finalPayload.put("contextId", effectiveSessionId);
            finalPayload.put("traceId", effectiveTraceId);
            sendEvent(emitter, "final", finalPayload);
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
                                                                                     AgentToolExecutionService.ToolExecutionObserver toolExecutionObserver,
                                                                                     String initialScratchpad,
                                                                                     int startTurn)
            throws TimeoutException, ExecutionException, InterruptedException {
        return CompletableFuture
                .supplyAsync(() -> orchestratorService.orchestrate(username, clientIp, request, toolExecutionObserver, initialScratchpad, startTurn), streamExecutor)
                .get(ORCHESTRATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private String buildLocalToolObservation(String previousScratchpad,
                                              int turn,
                                              String toolName,
                                              Map<String, Object> arguments,
                                              AgentLocalToolRelayService.LocalToolExecutionPayload payload) {
        StringBuilder builder = new StringBuilder();
        String prev = previousScratchpad == null ? "" : previousScratchpad;
        builder.append(prev);
        if (builder.length() > 0) {
            builder.append("\n\n");
        }
        String safeToolName = toolName == null ? "unknown" : toolName;
        String argJson = "{}";
        try {
            if (arguments != null && !arguments.isEmpty()) {
                argJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(arguments);
                if (argJson.length() > 1200) {
                    argJson = argJson.substring(0, 1200) + "...";
                }
            }
        } catch (Exception ignored) { }
        Map<String, Object> obsMap = new java.util.LinkedHashMap<>();
        obsMap.put("tool", payload.tool());
        obsMap.put("success", payload.success());
        obsMap.put("data", payload.result() == null ? Map.of() : payload.result());
        obsMap.put("error", payload.error() == null ? "" : payload.error());
        String obsJson = "{}";
        try {
            obsJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obsMap);
            if (obsJson.length() > 8000) {
                obsJson = obsJson.substring(0, 8000) + "...";
            }
        } catch (Exception ignored) { }
        builder.append("Turn ").append(turn).append(':').append("\n")
                .append("Action: ").append(safeToolName).append("\n")
                .append("Action Input: ").append(argJson).append("\n")
                .append("Observation: ").append(obsJson);
        return builder.toString();
    }

    private boolean isClientLocalTool(String toolName) {
        String safeToolName = toolName == null ? "" : toolName.trim().toLowerCase();
        return safeToolName.startsWith("file.")
                || safeToolName.startsWith("cmd.")
                || "web.search".equals(safeToolName);
    }

    private boolean requiresLocalToolAuthorization(MihtnelisAgentOrchestratorService.PendingLocalTool pendingLocalTool) {
        if (pendingLocalTool == null) {
            return false;
        }
        String riskLevel = pendingLocalTool.riskLevel() == null ? "" : pendingLocalTool.riskLevel().trim().toLowerCase();
        if ("high".equals(riskLevel)) {
            return true;
        }
        String tool = pendingLocalTool.tool() == null ? "" : pendingLocalTool.tool().trim().toLowerCase();
        return tool.startsWith("file.delete") || tool.startsWith("cmd.exec");
    }

    private String buildLocalToolAuthorizationMessage(String toolName, String purpose) {
        String safeToolName = toolName == null ? "" : toolName.trim().toLowerCase();
        String safePurpose = purpose == null ? "" : purpose.trim();
        String suffix = safePurpose.isBlank() ? "" : "\n用途：" + safePurpose;
        if (safeToolName.startsWith("file.delete")) {
            return "Agent 请求删除本地文件/目录，是否允许执行？" + suffix;
        }
        if (safeToolName.startsWith("cmd.exec")) {
            return "Agent 请求执行本地命令，是否允许执行？" + suffix;
        }
        return "Agent 请求执行高风险本地操作，是否允许执行？" + suffix;
    }

    private String normalizeToolPurpose(String toolName, String purpose) {
        String safePurpose = purpose == null ? "" : purpose.trim();
        if (!safePurpose.isBlank()) {
            return safePurpose;
        }
        String safeToolName = toolName == null ? "" : toolName.trim();
        if (!safeToolName.isBlank()) {
            return "为完成当前请求，执行 " + safeToolName + " 获取必要结果";
        }
        return "为完成当前请求，执行本地工具获取必要结果";
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
        String sessionId = request == null || request.sessionId() == null ? "" : request.sessionId().trim();
        if (sessionId.isBlank()) {
            return "sess-" + System.currentTimeMillis();
        }
        return sessionId;
    }

    private String normalizeTraceId(String traceId) {
        String value = traceId == null ? "" : traceId.trim();
        if (!value.isBlank()) {
            return value;
        }
        return "trace-" + UUID.randomUUID();
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

    private static final Pattern FINAL_ANSWER_REGEX =
            Pattern.compile("\\{\\s*\"type\"\\s*:\\s*\"final\"\\s*,\\s*\"answer\"\\s*:\\s*\"", Pattern.DOTALL);

    private String unwrapFinalEnvelope(String answer) {
        String source = answer == null ? "" : answer.trim();
        if (source.isBlank()) {
            return source;
        }
        // 如果不含 JSON 特征，直接返回
        if (!source.contains("{")) {
            return source;
        }
        // 严格解析失败时再尝试修复字符串内未转义的换行/制表符等控制字符。
        for (String candidate : new String[]{source, AgentJsonUtils.repairLiteralNewlinesInStrings(source)}) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            try {
                JsonNode root = OBJECT_MAPPER.readTree(candidate);
                String answerField = root.path("answer").asText("").trim();
                if (!answerField.isBlank()) {
                    return answerField;
                }
                // tool_call 信封泄漏 → 返回通用提示
                String type = root.path("type").asText("").trim().toLowerCase();
                if ("tool_call".equals(type)) {
                    return "";
                }
            } catch (Exception ignored) {
                // try next candidate
            }
        }
        // 尝试从混合文本中提取 JSON 信封（仅在确实包含有效 answer 字段时才提取）
        int braceStart = source.indexOf('{');
        int braceEnd = source.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            String jsonCandidate = source.substring(braceStart, braceEnd + 1);
            for (String jc : new String[]{jsonCandidate, AgentJsonUtils.repairLiteralNewlinesInStrings(jsonCandidate)}) {
                if (jc == null || jc.isBlank()) {
                    continue;
                }
                try {
                    JsonNode root = OBJECT_MAPPER.readTree(jc);
                    String answerField = root.path("answer").asText("").trim();
                    if (!answerField.isBlank()) {
                        return answerField;
                    }
                } catch (Exception ignored) { }
            }
        }
        // 正则回退：当 JSON 解析全部失败时（answer 内含未转义的控制字符），
        // 通过匹配 {"type":"final","answer":"  开头，从右侧剥离末尾的 "} 来提取。
        String regexExtracted = regexExtractFinalAnswer(source);
        if (regexExtracted != null) {
            return regexExtracted;
        }
        return source;
    }

    /**
     * 正则回退提取 final answer —— 当标准 JSON 解析失败时使用。
     * 匹配 {"type":"final","answer":" 开头，然后取到最后的 "} 之间的内容。
     */
    private String regexExtractFinalAnswer(String source) {
        Matcher matcher = FINAL_ANSWER_REGEX.matcher(source);
        if (!matcher.find()) {
            return null;
        }
        int contentStart = matcher.end();
        // 从末尾向前找最后一个 "} 或 "\n} 作为结束标记
        int closingQuote = -1;
        for (int i = source.length() - 1; i > contentStart; i--) {
            char c = source.charAt(i);
            if (c == '}') continue;
            if (c == '\n' || c == '\r' || c == ' ' || c == '\t') continue;
            if (c == '"') {
                // 确保不是转义的引号
                int backslashCount = 0;
                for (int j = i - 1; j >= contentStart; j--) {
                    if (source.charAt(j) == '\\') backslashCount++;
                    else break;
                }
                if (backslashCount % 2 == 0) {
                    closingQuote = i;
                    break;
                }
            } else {
                break;
            }
        }
        if (closingQuote <= contentStart) {
            return null;
        }
        String raw = source.substring(contentStart, closingQuote);
        // 反转义基本 JSON 转义序列
        return raw.replace("\\n", "\n")
                  .replace("\\r", "\r")
                  .replace("\\t", "\t")
                  .replace("\\\"", "\"")
                  .replace("\\\\", "\\")
                  .trim();
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
    public record SkillEntry(String name, String content) {
    }

    public record MihtnelisStreamRequest(String sessionId,
                                         String message,
                                         String provider,
                                         String model,
                                         String context,
                                         java.util.List<String> workspaces,
                                         java.util.List<SkillEntry> skills,
                                         Boolean thinking,
                                         String reasoningEffort) {
    }
}
