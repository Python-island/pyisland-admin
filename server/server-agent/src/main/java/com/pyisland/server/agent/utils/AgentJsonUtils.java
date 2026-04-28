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

    /**
     * 修复 LLM 输出 JSON 中字符串字面量内未转义的换行/回车/制表符，
     * 使其符合标准 JSON。其他 JSON 语法错误不在修复范围。
     *
     * @param json 可能包含字面换行的 JSON 文本
     * @return 修复后的 JSON 文本；输入为空返回原值
     */
    public static String repairLiteralNewlinesInStrings(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        StringBuilder result = new StringBuilder(json.length() + 16);
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (escaped) {
                    result.append(c);
                    escaped = false;
                    continue;
                }
                if (c == '\\') {
                    result.append(c);
                    escaped = true;
                    continue;
                }
                if (c == '"') {
                    result.append(c);
                    inString = false;
                    continue;
                }
                if (c == '\n') {
                    result.append("\\n");
                    continue;
                }
                if (c == '\r') {
                    result.append("\\r");
                    continue;
                }
                if (c == '\t') {
                    result.append("\\t");
                    continue;
                }
                result.append(c);
            } else {
                result.append(c);
                if (c == '"') {
                    inString = true;
                }
            }
        }
        return result.toString();
    }
}
