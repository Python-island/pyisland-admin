package com.pyisland.server.agent.service;

import com.pyisland.server.agent.utils.AgentStringUtils;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Agent URL 授权状态服务（内存态）。
 */
@Service
public class AgentWebAuthorizationService {

    private final Map<String, PendingRequest> pendingRequests = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Boolean>> decisionFutures = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Instant>> oneTimeAllowedUrlsByUser = new ConcurrentHashMap<>();

    public String createPendingRequest(String username, String rawUrl) {
        String safeUser = AgentStringUtils.trimToEmpty(username);
        String normalizedUrl = normalizeUrl(rawUrl);
        if (safeUser.isBlank() || normalizedUrl.isBlank()) {
            return "";
        }
        String requestId = UUID.randomUUID().toString();
        pendingRequests.put(requestId, new PendingRequest(requestId, safeUser, normalizedUrl, Instant.now()));
        decisionFutures.put(requestId, new CompletableFuture<>());
        return requestId;
    }

    public boolean consumeOneTimeGrant(String username, String rawUrl) {
        String safeUser = AgentStringUtils.trimToEmpty(username);
        String normalizedUrl = normalizeUrl(rawUrl);
        if (safeUser.isBlank() || normalizedUrl.isBlank()) {
            return false;
        }
        Map<String, Instant> grantedByUrl = oneTimeAllowedUrlsByUser.get(safeUser);
        if (grantedByUrl == null) {
            return false;
        }
        Instant removed = grantedByUrl.remove(normalizedUrl);
        if (grantedByUrl.isEmpty()) {
            oneTimeAllowedUrlsByUser.remove(safeUser);
        }
        return removed != null;
    }

    public ResolveResult resolve(String username, String requestId, boolean allow) {
        String safeUser = AgentStringUtils.trimToEmpty(username);
        String safeRequestId = AgentStringUtils.trimToEmpty(requestId);
        if (safeUser.isBlank() || safeRequestId.isBlank()) {
            return ResolveResult.failure("requestId is required");
        }
        PendingRequest pending = pendingRequests.get(safeRequestId);
        if (pending == null) {
            return ResolveResult.failure("pending request not found");
        }
        if (!Objects.equals(safeUser, pending.username())) {
            return ResolveResult.failure("request does not belong to current user");
        }
        pendingRequests.remove(safeRequestId);
        if (allow) {
            oneTimeAllowedUrlsByUser
                    .computeIfAbsent(safeUser, key -> new ConcurrentHashMap<>())
                    .put(pending.url(), Instant.now());
        }
        CompletableFuture<Boolean> decisionFuture = decisionFutures.remove(safeRequestId);
        if (decisionFuture != null && !decisionFuture.isDone()) {
            decisionFuture.complete(allow);
        }
        return ResolveResult.success(Map.of(
                "requestId", pending.requestId(),
                "url", pending.url(),
                "allowed", allow,
                "resolvedAt", Instant.now().toString()
        ));
    }

    public AwaitResult awaitDecision(String username, String requestId, long timeoutSeconds) {
        String safeUser = AgentStringUtils.trimToEmpty(username);
        String safeRequestId = AgentStringUtils.trimToEmpty(requestId);
        if (safeUser.isBlank() || safeRequestId.isBlank()) {
            return AwaitResult.failure("requestId is required");
        }
        PendingRequest pending = pendingRequests.get(safeRequestId);
        if (pending == null) {
            return AwaitResult.failure("pending request not found");
        }
        if (!Objects.equals(safeUser, pending.username())) {
            return AwaitResult.failure("request does not belong to current user");
        }
        CompletableFuture<Boolean> decisionFuture = decisionFutures.computeIfAbsent(safeRequestId, key -> new CompletableFuture<>());
        long waitSeconds = Math.max(1L, timeoutSeconds);
        try {
            boolean allowed = decisionFuture.get(waitSeconds, TimeUnit.SECONDS);
            return AwaitResult.resolved(allowed);
        } catch (TimeoutException timeoutException) {
            pendingRequests.remove(safeRequestId);
            decisionFutures.remove(safeRequestId);
            return AwaitResult.failure("authorization decision timeout");
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return AwaitResult.failure("authorization wait interrupted");
        } catch (Exception exception) {
            return AwaitResult.failure("authorization wait failed");
        }
    }

    private String normalizeUrl(String rawUrl) {
        String safe = AgentStringUtils.trimToEmpty(rawUrl);
        if (safe.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(safe);
            String scheme = AgentStringUtils.trimToEmpty(uri.getScheme()).toLowerCase();
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return "";
            }
            String host = AgentStringUtils.trimToEmpty(uri.getHost()).toLowerCase();
            if (host.isBlank()) {
                return "";
            }
            URI normalized = new URI(
                    scheme,
                    uri.getUserInfo(),
                    host,
                    uri.getPort(),
                    uri.getPath(),
                    uri.getQuery(),
                    null
            );
            return normalized.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private record PendingRequest(String requestId, String username, String url, Instant createdAt) {
    }

    public record ResolveResult(boolean success, String error, Map<String, Object> data) {
        public static ResolveResult success(Map<String, Object> data) {
            return new ResolveResult(true, "", data == null ? Map.of() : data);
        }

        public static ResolveResult failure(String error) {
            return new ResolveResult(false, AgentStringUtils.trimToEmpty(error), Map.of());
        }
    }

    public record AwaitResult(boolean resolved, boolean allowed, String error) {
        public static AwaitResult resolved(boolean allowed) {
            return new AwaitResult(true, allowed, "");
        }

        public static AwaitResult failure(String error) {
            return new AwaitResult(false, false, AgentStringUtils.trimToEmpty(error));
        }
    }
}
