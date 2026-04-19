package com.pyisland.server.auth.security;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 接口防重放过滤器。
 * 对高危写接口要求携带 X-Timestamp 与 X-Nonce，并在短时间窗口内拒绝重复 nonce。
 */
@Component
public class ReplayProtectionFilter extends OncePerRequestFilter {

    private static final long ALLOWED_SKEW_MILLIS = 5 * 60 * 1000L;
    private static final int CLEANUP_EVERY = 256;
    private static final Set<String> PROTECTED_METHODS = Set.of("POST", "PUT", "DELETE");

    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Long> nonceSeenAt = new ConcurrentHashMap<>();
    private final AtomicLong requestCounter = new AtomicLong(0);

    public ReplayProtectionFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!isProtectedRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication != null ? authentication.getName() : null;
        if (username == null || username.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        String timestampHeader = request.getHeader("X-Timestamp");
        String nonce = request.getHeader("X-Nonce");
        if (timestampHeader == null || timestampHeader.isBlank() || nonce == null || nonce.isBlank()) {
            writeError(response, 4002, "缺少防重放请求头");
            return;
        }
        if (nonce.length() < 8 || nonce.length() > 128) {
            writeError(response, 4002, "非法 nonce");
            return;
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader.trim());
        } catch (NumberFormatException ex) {
            writeError(response, 4002, "非法时间戳");
            return;
        }

        long now = Instant.now().toEpochMilli();
        if (Math.abs(now - timestamp) > ALLOWED_SKEW_MILLIS) {
            writeError(response, 4002, "请求时间窗口已过期");
            return;
        }

        String key = username + ":" + request.getMethod() + ":" + request.getRequestURI() + ":" + nonce;
        Long previous = nonceSeenAt.putIfAbsent(key, now);
        if (previous != null && now - previous <= ALLOWED_SKEW_MILLIS) {
            writeError(response, 4003, "检测到重放请求");
            return;
        }
        nonceSeenAt.put(key, now);

        if (requestCounter.incrementAndGet() % CLEANUP_EVERY == 0) {
            sweepExpired(now);
        }

        filterChain.doFilter(request, response);
    }

    private boolean isProtectedRequest(HttpServletRequest request) {
        if (!PROTECTED_METHODS.contains(request.getMethod())) {
            return false;
        }
        String uri = request.getRequestURI();
        return uri.startsWith("/v1/user/") || "/v1/upload/user-avatar".equals(uri);
    }

    private void sweepExpired(long now) {
        nonceSeenAt.entrySet().removeIf(entry -> now - entry.getValue() > ALLOWED_SKEW_MILLIS);
    }

    private void writeError(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
