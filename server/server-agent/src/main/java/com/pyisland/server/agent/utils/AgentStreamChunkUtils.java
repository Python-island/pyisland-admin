package com.pyisland.server.agent.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 流式分块工具。
 */
public final class AgentStreamChunkUtils {

    private AgentStreamChunkUtils() {
    }

    public static List<String> splitForStreaming(String answer) {
        String source = AgentStringUtils.trimToEmpty(answer);
        if (source.isBlank()) {
            return List.of();
        }
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            current.append(c);
            boolean punctuationBoundary = c == '，' || c == '。' || c == '！' || c == '？' || c == ',' || c == '.' || c == '!' || c == '?';
            boolean lengthBoundary = current.length() >= 20;
            if (punctuationBoundary || lengthBoundary) {
                chunks.add(current.toString());
                current.setLength(0);
            }
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }
        return chunks;
    }
}
