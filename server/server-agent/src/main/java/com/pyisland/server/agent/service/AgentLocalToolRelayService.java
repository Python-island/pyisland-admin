package com.pyisland.server.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.pyisland.server.agent.utils.AgentStringUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Agent 本地工具执行中继服务（内存态）。
 */
@Service
public class AgentLocalToolRelayService {

    private final Map<String, PendingLocalToolRequest> pendingRequests = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<LocalToolExecutionPayload>> decisionFutures = new ConcurrentHashMap<>();
    private final Map<String, LocalToolExecutionPayload> consumableResultsBySignature = new ConcurrentHashMap<>();
    private final Map<String, Boolean> authorizationDecisions = new ConcurrentHashMap<>();
    private final ObjectMapper digestObjectMapper;

    public AgentLocalToolRelayService() {
        this.digestObjectMapper = new ObjectMapper();
        this.digestObjectMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public PendingRequestResult createPendingRequest(String username,
                                                     String tool,
                                                     Map<String, Object> arguments,
                                                     String riskLevel) {
        String safeUsername = AgentStringUtils.trimToEmpty(username);
        String safeTool = AgentStringUtils.trimToEmpty(tool);
        if (safeUsername.isBlank() || safeTool.isBlank()) {
            return null;
        }
        Map<String, Object> safeArguments = arguments == null ? Map.of() : arguments;
        String argumentsDigest = digestArguments(safeArguments);
        String requestId = UUID.randomUUID().toString();
        PendingLocalToolRequest pendingRequest = new PendingLocalToolRequest(
                requestId,
                safeUsername,
                safeTool,
                safeArguments,
                argumentsDigest,
                AgentStringUtils.trimToDefault(riskLevel, "high"),
                Instant.now()
        );
        pendingRequests.put(requestId, pendingRequest);
        decisionFutures.put(requestId, new CompletableFuture<>());
        authorizationDecisions.put(requestId, !requiresAuthorization(pendingRequest));
        return new PendingRequestResult(
                pendingRequest.requestId(),
                pendingRequest.tool(),
                pendingRequest.arguments(),
                pendingRequest.argumentsDigest(),
                pendingRequest.riskLevel()
        );
    }

    public ResolveResult resolve(String username,
                                 String requestId,
                                 boolean success,
                                 Object result,
                                 String error,
                                 Long durationMs) {
        String safeUser = AgentStringUtils.trimToEmpty(username);
        String safeRequestId = AgentStringUtils.trimToEmpty(requestId);
        if (safeUser.isBlank() || safeRequestId.isBlank()) {
            return ResolveResult.failure("requestId is required");
        }
        PendingLocalToolRequest pendingRequest = pendingRequests.get(safeRequestId);
        if (pendingRequest == null) {
            return ResolveResult.failure("pending local tool request not found");
        }
        if (!Objects.equals(safeUser, pendingRequest.username())) {
            return ResolveResult.failure("request does not belong to current user");
        }
        if (requiresAuthorization(pendingRequest) && !Boolean.TRUE.equals(authorizationDecisions.get(safeRequestId))) {
            return ResolveResult.failure("local tool authorization required");
        }

        pendingRequests.remove(safeRequestId);
        authorizationDecisions.remove(safeRequestId);
        LocalToolExecutionPayload payload = new LocalToolExecutionPayload(
                pendingRequest.requestId(),
                pendingRequest.tool(),
                pendingRequest.arguments(),
                pendingRequest.argumentsDigest(),
                success,
                result,
                AgentStringUtils.trimToEmpty(error),
                durationMs == null ? 0L : Math.max(0L, durationMs),
                Instant.now().toString()
        );
        consumableResultsBySignature.put(signature(safeUser, pendingRequest.tool(), pendingRequest.argumentsDigest()), payload);

        CompletableFuture<LocalToolExecutionPayload> decisionFuture = decisionFutures.remove(safeRequestId);
        if (decisionFuture != null && !decisionFuture.isDone()) {
            decisionFuture.complete(payload);
        }

        return ResolveResult.success(Map.of(
                "requestId", payload.requestId(),
                "tool", payload.tool(),
                "success", payload.success(),
                "resolvedAt", payload.resolvedAt()
        ));
    }

    public ResolveAuthorizationResult resolveAuthorization(String username,
                                                           String requestId,
                                                           boolean allow) {
        String safeUser = AgentStringUtils.trimToEmpty(username);
        String safeRequestId = AgentStringUtils.trimToEmpty(requestId);
        if (safeUser.isBlank() || safeRequestId.isBlank()) {
            return ResolveAuthorizationResult.failure("requestId is required");
        }
        PendingLocalToolRequest pendingRequest = pendingRequests.get(safeRequestId);
        if (pendingRequest == null) {
            return ResolveAuthorizationResult.failure("pending local tool request not found");
        }
        if (!Objects.equals(safeUser, pendingRequest.username())) {
            return ResolveAuthorizationResult.failure("request does not belong to current user");
        }
        if (!requiresAuthorization(pendingRequest)) {
            return ResolveAuthorizationResult.success(Map.of(
                    "requestId", pendingRequest.requestId(),
                    "tool", pendingRequest.tool(),
                    "allow", true,
                    "resolvedAt", Instant.now().toString()
            ));
        }
        if (!allow) {
            pendingRequests.remove(safeRequestId);
            authorizationDecisions.remove(safeRequestId);
            LocalToolExecutionPayload deniedPayload = new LocalToolExecutionPayload(
                    pendingRequest.requestId(),
                    pendingRequest.tool(),
                    pendingRequest.arguments(),
                    pendingRequest.argumentsDigest(),
                    false,
                    Map.of(),
                    "LOCAL_TOOL_DENIED_BY_USER",
                    0L,
                    Instant.now().toString()
            );
            CompletableFuture<LocalToolExecutionPayload> decisionFuture = decisionFutures.remove(safeRequestId);
            if (decisionFuture != null && !decisionFuture.isDone()) {
                decisionFuture.complete(deniedPayload);
            }
            return ResolveAuthorizationResult.success(Map.of(
                    "requestId", deniedPayload.requestId(),
                    "tool", deniedPayload.tool(),
                    "allow", false,
                    "resolvedAt", deniedPayload.resolvedAt()
            ));
        }
        authorizationDecisions.put(safeRequestId, true);
        return ResolveAuthorizationResult.success(Map.of(
                "requestId", pendingRequest.requestId(),
                "tool", pendingRequest.tool(),
                "allow", true,
                "resolvedAt", Instant.now().toString()
        ));
    }

    public AwaitResult awaitResult(String username, String requestId, long timeoutSeconds) {
        String safeUser = AgentStringUtils.trimToEmpty(username);
        String safeRequestId = AgentStringUtils.trimToEmpty(requestId);
        if (safeUser.isBlank() || safeRequestId.isBlank()) {
            return AwaitResult.failure("requestId is required");
        }
        PendingLocalToolRequest pendingRequest = pendingRequests.get(safeRequestId);
        if (pendingRequest == null) {
            return AwaitResult.failure("pending local tool request not found");
        }
        if (!Objects.equals(safeUser, pendingRequest.username())) {
            return AwaitResult.failure("request does not belong to current user");
        }

        CompletableFuture<LocalToolExecutionPayload> decisionFuture = decisionFutures.computeIfAbsent(
                safeRequestId,
                key -> new CompletableFuture<>()
        );
        long waitSeconds = Math.max(1L, timeoutSeconds);
        try {
            LocalToolExecutionPayload payload = decisionFuture.get(waitSeconds, TimeUnit.SECONDS);
            return AwaitResult.resolved(payload);
        } catch (TimeoutException timeoutException) {
            pendingRequests.remove(safeRequestId);
            decisionFutures.remove(safeRequestId);
            authorizationDecisions.remove(safeRequestId);
            return AwaitResult.failure("local tool execution decision timeout");
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return AwaitResult.failure("local tool execution wait interrupted");
        } catch (Exception exception) {
            return AwaitResult.failure("local tool execution wait failed");
        }
    }

    public record ResolveAuthorizationResult(boolean success, String error, Map<String, Object> data) {
        public static ResolveAuthorizationResult success(Map<String, Object> data) {
            return new ResolveAuthorizationResult(true, "", data == null ? Map.of() : data);
        }

        public static ResolveAuthorizationResult failure(String error) {
            return new ResolveAuthorizationResult(false, AgentStringUtils.trimToEmpty(error), Map.of());
        }
    }

    public LocalToolExecutionPayload consumeResolvedTool(String username,
                                                         String tool,
                                                         Map<String, Object> arguments) {
        String safeUser = AgentStringUtils.trimToEmpty(username);
        String safeTool = AgentStringUtils.trimToEmpty(tool);
        if (safeUser.isBlank() || safeTool.isBlank()) {
            return null;
        }
        String argumentsDigest = digestArguments(arguments == null ? Map.of() : arguments);
        return consumableResultsBySignature.remove(signature(safeUser, safeTool, argumentsDigest));
    }

    private String signature(String username, String tool, String argumentsDigest) {
        return AgentStringUtils.trimToEmpty(username)
                + "|" + AgentStringUtils.trimToEmpty(tool)
                + "|" + AgentStringUtils.trimToEmpty(argumentsDigest);
    }

    private String digestArguments(Map<String, Object> arguments) {
        try {
            return digestObjectMapper.writeValueAsString(arguments == null ? Map.of() : arguments);
        } catch (Exception ignored) {
            return "{}";
        }
    }

    private boolean requiresAuthorization(PendingLocalToolRequest request) {
        if (request == null) {
            return false;
        }
        String riskLevel = AgentStringUtils.trimToEmpty(request.riskLevel()).toLowerCase(Locale.ROOT);
        if ("high".equals(riskLevel)) {
            return true;
        }
        String tool = AgentStringUtils.trimToEmpty(request.tool()).toLowerCase(Locale.ROOT);
        return tool.startsWith("file.delete")
                || tool.startsWith("file.rename")
                || tool.startsWith("cmd.exec")
                || tool.startsWith("cmd.powershell")
                || tool.startsWith("win.close")
                || tool.startsWith("win.minimize")
                || tool.startsWith("win.maximize")
                || tool.startsWith("win.restore");
    }

    private record PendingLocalToolRequest(String requestId,
                                           String username,
                                           String tool,
                                           Map<String, Object> arguments,
                                           String argumentsDigest,
                                           String riskLevel,
                                           Instant createdAt) {
    }

    public record PendingRequestResult(String requestId,
                                       String tool,
                                       Map<String, Object> arguments,
                                       String argumentsDigest,
                                       String riskLevel) {
    }

    public record LocalToolExecutionPayload(String requestId,
                                            String tool,
                                            Map<String, Object> arguments,
                                            String argumentsDigest,
                                            boolean success,
                                            Object result,
                                            String error,
                                            long durationMs,
                                            String resolvedAt) {
    }

    public record ResolveResult(boolean success, String error, Map<String, Object> data) {
        public static ResolveResult success(Map<String, Object> data) {
            return new ResolveResult(true, "", data == null ? Map.of() : data);
        }

        public static ResolveResult failure(String error) {
            return new ResolveResult(false, AgentStringUtils.trimToEmpty(error), Map.of());
        }
    }

    public record AwaitResult(boolean resolved, LocalToolExecutionPayload payload, String error) {
        public static AwaitResult resolved(LocalToolExecutionPayload payload) {
            return new AwaitResult(true, payload, "");
        }

        public static AwaitResult failure(String error) {
            return new AwaitResult(false, null, AgentStringUtils.trimToEmpty(error));
        }
    }
}
