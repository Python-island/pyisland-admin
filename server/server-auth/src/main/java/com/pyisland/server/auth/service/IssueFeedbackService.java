package com.pyisland.server.auth.service;

import com.pyisland.server.auth.mapper.IssueFeedbackMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class IssueFeedbackService {

    private final IssueFeedbackMapper issueFeedbackMapper;
    private final StringRedisTemplate issueFeedbackRedisTemplate;

    public IssueFeedbackService(IssueFeedbackMapper issueFeedbackMapper,
                                @Qualifier("issueFeedbackRedisTemplate") StringRedisTemplate issueFeedbackRedisTemplate) {
        this.issueFeedbackMapper = issueFeedbackMapper;
        this.issueFeedbackRedisTemplate = issueFeedbackRedisTemplate;
    }

    public boolean submit(String username,
                          String userIp,
                          String feedbackType,
                          String title,
                          String content,
                          String contact,
                          String feedbackLogUrl,
                          String clientVersion) {
        String normalizedUsername = safeText(username, 100).toLowerCase();
        String normalizedIp = normalizeIp(userIp);
        if (!checkRateLimit("issue-feedback:submit:" + normalizedUsername + ":" + normalizedIp, 3600, 3)) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        return issueFeedbackMapper.insertFeedback(
                normalizedUsername,
                normalizeFeedbackType(feedbackType),
                safeTitle(title),
                safeContent(content),
                safeText(contact, 150),
                safeText(feedbackLogUrl, 500),
                safeText(clientVersion, 50),
                "pending",
                now,
                now
        ) > 0;
    }

    public List<Map<String, Object>> listMine(String username, String status, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        int offset = (safePage - 1) * safeSize;
        return issueFeedbackMapper.listMine(safeText(username, 100), normalizeStatus(status), offset, safeSize);
    }

    public long countMine(String username, String status) {
        return issueFeedbackMapper.countMine(safeText(username, 100), normalizeStatus(status));
    }

    public List<Map<String, Object>> listAdmin(String status, String keyword, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        int offset = (safePage - 1) * safeSize;
        return issueFeedbackMapper.listAdmin(normalizeStatus(status), safeText(keyword, 120), offset, safeSize);
    }

    public long countAdmin(String status, String keyword) {
        return issueFeedbackMapper.countAdmin(normalizeStatus(status), safeText(keyword, 120));
    }

    public boolean resolve(Long id, String status, String adminReply) {
        if (id == null || id <= 0) {
            return false;
        }
        String normalizedStatus = normalizeResolveStatus(status);
        LocalDateTime now = LocalDateTime.now();
        return issueFeedbackMapper.resolve(id,
                normalizedStatus,
                safeText(adminReply, 1000),
                "resolved".equals(normalizedStatus) ? now : null,
                now) > 0;
    }

    private boolean checkRateLimit(String key, int windowSeconds, int maxCount) {
        Long count = issueFeedbackRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            issueFeedbackRedisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
        }
        return count != null && count <= maxCount;
    }

    private String normalizeFeedbackType(String type) {
        String normalized = safeText(type, 40).toLowerCase();
        if (normalized.isBlank()) {
            return "general";
        }
        return normalized;
    }

    private String normalizeStatus(String status) {
        String normalized = safeText(status, 20).toLowerCase();
        if (normalized.isBlank()) {
            return "";
        }
        if (!"pending".equals(normalized)
                && !"resolved".equals(normalized)
                && !"rejected".equals(normalized)) {
            return "";
        }
        return normalized;
    }

    private String normalizeResolveStatus(String status) {
        String normalized = safeText(status, 20).toLowerCase();
        if ("rejected".equals(normalized)) {
            return "rejected";
        }
        return "resolved";
    }

    private String normalizeIp(String ip) {
        String normalized = safeText(ip, 64);
        if (normalized.isBlank()) {
            return "unknown";
        }
        return normalized;
    }

    private String safeTitle(String value) {
        String normalized = safeText(value, 120);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("反馈标题不能为空");
        }
        return normalized;
    }

    private String safeContent(String value) {
        String normalized = safeText(value, 5000);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("反馈内容不能为空");
        }
        return normalized;
    }

    private String safeText(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            return trimmed.substring(0, maxLength);
        }
        return trimmed;
    }
}
