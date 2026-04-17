package com.pyisland.server.common.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端 IP 提取工具。
 * 优先从常见代理头解析，降级为远端地址。
 */
public final class ClientIpUtil {

    private ClientIpUtil() {
    }

    /**
     * 提取客户端 IP。
     * @param request HTTP 请求。
     * @return 客户端 IP。
     */
    public static String resolve(HttpServletRequest request) {
        String ip = firstValid(request.getHeader("X-Forwarded-For"));
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        if (ip == null || ip.isBlank()) {
            ip = firstValid(request.getHeader("X-Real-IP"));
        }
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }
        return ip == null ? "unknown" : ip;
    }

    private static String firstValid(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isBlank() || "unknown".equalsIgnoreCase(trimmed)) {
            return null;
        }
        return trimmed;
    }
}
