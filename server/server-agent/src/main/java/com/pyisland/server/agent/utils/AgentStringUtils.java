package com.pyisland.server.agent.utils;

import java.util.Locale;

/**
 * Agent 字符串工具。
 */
public final class AgentStringUtils {

    private AgentStringUtils() {
    }

    public static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    public static String trimToDefault(String value, String fallback) {
        String safe = trimToEmpty(value);
        return safe.isBlank() ? trimToEmpty(fallback) : safe;
    }

    public static String lowerTrim(String value) {
        return trimToEmpty(value).toLowerCase(Locale.ROOT);
    }

    public static String trimTrailingSlash(String value) {
        String safe = trimToEmpty(value);
        if (safe.endsWith("/")) {
            return safe.substring(0, safe.length() - 1);
        }
        return safe;
    }

    public static String toStringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
