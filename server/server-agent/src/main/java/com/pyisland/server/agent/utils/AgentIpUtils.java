package com.pyisland.server.agent.utils;

/**
 * Agent IP 工具。
 */
public final class AgentIpUtils {

    private AgentIpUtils() {
    }

    public static String sanitizeIp(String value) {
        String ip = AgentStringUtils.trimToEmpty(value);
        if (ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            return "";
        }
        if (ip.startsWith("::ffff:")) {
            ip = ip.substring("::ffff:".length()).trim();
        }
        if (ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
