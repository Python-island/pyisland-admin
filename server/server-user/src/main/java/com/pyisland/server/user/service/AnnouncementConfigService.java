package com.pyisland.server.user.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pyisland.server.user.entity.AnnouncementConfig;
import com.pyisland.server.user.mapper.AnnouncementConfigMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 公告配置服务。
 */
@Service
public class AnnouncementConfigService {

    private static final Long SINGLETON_ID = 1L;
    private static final String ANNOUNCEMENT_CACHE_KEY = "announcement:current:v1";
    private static final String ANNOUNCEMENT_NONE_CACHE_VALUE = "__NONE__";

    private final AnnouncementConfigMapper announcementConfigMapper;
    private final StringRedisTemplate announcementRedisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final long cacheTtlSeconds;

    public AnnouncementConfigService(AnnouncementConfigMapper announcementConfigMapper,
                                     @Qualifier("announcementRedisTemplate") StringRedisTemplate announcementRedisTemplate,
                                     @Value("${announcement.cache-ttl-seconds:60}") long cacheTtlSeconds) {
        this.announcementConfigMapper = announcementConfigMapper;
        this.announcementRedisTemplate = announcementRedisTemplate;
        this.cacheTtlSeconds = Math.max(5, cacheTtlSeconds);
    }

    public AnnouncementConfig getAdminConfig() {
        AnnouncementConfig config = announcementConfigMapper.selectCurrent();
        if (config != null) {
            return config;
        }
        return buildDefault();
    }

    public Map<String, Object> getPublicAnnouncement() {
        String cachedRaw = readCacheRaw();
        if (cachedRaw != null) {
            if (ANNOUNCEMENT_NONE_CACHE_VALUE.equals(cachedRaw)) {
                return null;
            }
            try {
                return objectMapper.readValue(cachedRaw, new TypeReference<LinkedHashMap<String, Object>>() {
                });
            } catch (Exception ignored) {
                // 缓存损坏时回源数据库
            }
        }

        AnnouncementConfig config = announcementConfigMapper.selectCurrent();
        Map<String, Object> data = buildPublicPayload(config, LocalDateTime.now());
        writeCache(data);
        return data;
    }

    @Transactional
    public AnnouncementConfig save(String title,
                                   String content,
                                   Boolean enabled,
                                   String startAt,
                                   String endAt,
                                   String updatedBy) {
        String normalizedTitle = trimToEmpty(title);
        String normalizedContent = trimToEmpty(content);
        boolean normalizedEnabled = enabled != null && enabled;
        LocalDateTime parsedStartAt = parseTime(startAt, "startAt");
        LocalDateTime parsedEndAt = parseTime(endAt, "endAt");

        if (parsedStartAt != null && parsedEndAt != null && parsedStartAt.isAfter(parsedEndAt)) {
            throw new IllegalArgumentException("startAt 不能晚于 endAt");
        }
        if (normalizedEnabled && normalizedTitle.isBlank() && normalizedContent.isBlank()) {
            throw new IllegalArgumentException("启用公告时标题和内容不能同时为空");
        }

        LocalDateTime now = LocalDateTime.now();
        AnnouncementConfig current = announcementConfigMapper.selectById(SINGLETON_ID);
        if (current == null) {
            AnnouncementConfig creating = new AnnouncementConfig();
            creating.setId(SINGLETON_ID);
            creating.setTitle(normalizedTitle);
            creating.setContent(normalizedContent);
            creating.setEnabled(normalizedEnabled);
            creating.setStartAt(parsedStartAt);
            creating.setEndAt(parsedEndAt);
            creating.setUpdatedBy(trimToEmpty(updatedBy));
            creating.setUpdatedAt(now);
            announcementConfigMapper.insert(creating);
        } else {
            announcementConfigMapper.update(
                    SINGLETON_ID,
                    normalizedTitle,
                    normalizedContent,
                    normalizedEnabled,
                    parsedStartAt,
                    parsedEndAt,
                    trimToEmpty(updatedBy),
                    now
            );
        }
        AnnouncementConfig saved = announcementConfigMapper.selectById(SINGLETON_ID);
        writeCache(buildPublicPayload(saved, now));
        return saved;
    }

    private Map<String, Object> buildPublicPayload(AnnouncementConfig config, LocalDateTime now) {
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            return null;
        }
        if (config.getStartAt() != null && now.isBefore(config.getStartAt())) {
            return null;
        }
        if (config.getEndAt() != null && now.isAfter(config.getEndAt())) {
            return null;
        }
        if (isBlank(config.getTitle()) && isBlank(config.getContent())) {
            return null;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("title", safe(config.getTitle()));
        data.put("content", safe(config.getContent()));
        data.put("startAt", config.getStartAt() == null ? null : config.getStartAt().toString());
        data.put("endAt", config.getEndAt() == null ? null : config.getEndAt().toString());
        data.put("updatedAt", config.getUpdatedAt() == null ? null : config.getUpdatedAt().toString());
        return data;
    }

    private String readCacheRaw() {
        try {
            return announcementRedisTemplate.opsForValue().get(ANNOUNCEMENT_CACHE_KEY);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void writeCache(Map<String, Object> data) {
        try {
            String value = data == null
                    ? ANNOUNCEMENT_NONE_CACHE_VALUE
                    : objectMapper.writeValueAsString(data);
            announcementRedisTemplate.opsForValue().set(ANNOUNCEMENT_CACHE_KEY, value);
            announcementRedisTemplate.expire(ANNOUNCEMENT_CACHE_KEY, java.time.Duration.ofSeconds(cacheTtlSeconds));
        } catch (Exception ignored) {
        }
    }

    private AnnouncementConfig buildDefault() {
        AnnouncementConfig cfg = new AnnouncementConfig();
        cfg.setId(SINGLETON_ID);
        cfg.setTitle("");
        cfg.setContent("");
        cfg.setEnabled(false);
        return cfg;
    }

    private LocalDateTime parseTime(String value, String fieldName) {
        String raw = trimToEmpty(value);
        if (raw.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw);
        } catch (Exception ex) {
            throw new IllegalArgumentException(fieldName + " 时间格式非法，请使用 ISO-8601（例如 2026-04-23T21:30:00）");
        }
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
