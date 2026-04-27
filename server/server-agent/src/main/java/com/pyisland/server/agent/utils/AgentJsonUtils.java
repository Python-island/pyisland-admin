package com.pyisland.server.agent.utils;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent JSON 工具。
 */
public final class AgentJsonUtils {

    private AgentJsonUtils() {
    }

    public static Map<String, Object> toStringKeyMap(Object value) {
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                String key = entry.getKey() == null ? "" : String.valueOf(entry.getKey());
                map.put(key, entry.getValue());
            }
            return map;
        }
        return Map.of();
    }

    public static String toSafeJson(ObjectMapper objectMapper, Object value, int maxLength) {
        if (objectMapper == null) {
            return "{}";
        }
        try {
            String json = objectMapper.writeValueAsString(value);
            if (maxLength <= 0 || json.length() <= maxLength) {
                return json;
            }
            return json.substring(0, maxLength);
        } catch (Exception ignored) {
            return "{}";
        }
    }
}
