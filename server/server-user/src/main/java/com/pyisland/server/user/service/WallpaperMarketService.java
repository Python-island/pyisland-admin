package com.pyisland.server.user.service;

import com.pyisland.server.upload.service.WallpaperR2StorageService;
import com.pyisland.server.user.entity.WallpaperAsset;
import com.pyisland.server.user.mapper.WallpaperMarketMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 壁纸市场服务。
 */
@Service
public class WallpaperMarketService {

    private static final long MAX_IMAGE_SIZE = 20L * 1024 * 1024;

    private final WallpaperMarketMapper mapper;
    private final WallpaperR2StorageService wallpaperR2StorageService;
    private final StringRedisTemplate redisTemplate;

    public WallpaperMarketService(WallpaperMarketMapper mapper,
                                  WallpaperR2StorageService wallpaperR2StorageService,
                                  StringRedisTemplate redisTemplate) {
        this.mapper = mapper;
        this.wallpaperR2StorageService = wallpaperR2StorageService;
        this.redisTemplate = redisTemplate;
    }

    @CacheEvict(cacheNames = {"wallpaper-list", "wallpaper-admin-list"}, allEntries = true, cacheManager = "wallpaperCacheManager")
    public Long create(String ownerUsername,
                       String title,
                       String description,
                       String type,
                       String tagsText,
                       boolean copyrightDeclared,
                       MultipartFile original,
                       MultipartFile thumb320,
                       MultipartFile thumb720,
                       MultipartFile thumb1280,
                       Integer width,
                       Integer height) throws IOException {
        validateImageFile(original);
        validateImageFile(thumb320);
        validateImageFile(thumb720);
        validateImageFile(thumb1280);

        String originalUrl = wallpaperR2StorageService.upload(original, "wallpapers/original");
        String thumb320Url = wallpaperR2StorageService.upload(thumb320, "wallpapers/thumb-320");
        String thumb720Url = wallpaperR2StorageService.upload(thumb720, "wallpapers/thumb-720");
        String thumb1280Url = wallpaperR2StorageService.upload(thumb1280, "wallpapers/thumb-1280");

        LocalDateTime now = LocalDateTime.now();
        WallpaperAsset asset = new WallpaperAsset();
        asset.setOwnerUsername(ownerUsername);
        asset.setTitle(safeTitle(title));
        asset.setDescription(safeText(description, 2000));
        asset.setType(normalizeType(type));
        asset.setStatus("pending");
        asset.setOriginalUrl(originalUrl);
        asset.setThumb320Url(thumb320Url);
        asset.setThumb720Url(thumb720Url);
        asset.setThumb1280Url(thumb1280Url);
        asset.setWidth(width);
        asset.setHeight(height);
        asset.setFileSize(original.getSize());
        asset.setTagsText(safeText(tagsText, 500));
        asset.setCopyrightDeclared(copyrightDeclared);
        asset.setRatingAvg(java.math.BigDecimal.ZERO);
        asset.setRatingCount(0L);
        asset.setDownloadCount(0L);
        asset.setApplyCount(0L);
        asset.setCurrentVersion(1);
        asset.setDeleted(false);
        asset.setCreatedAt(now);
        asset.setUpdatedAt(now);
        asset.setPublishedAt(null);
        mapper.insertAsset(asset);

        mapper.insertVersion(asset.getId(),
                1,
                originalUrl,
                thumb320Url,
                thumb720Url,
                thumb1280Url,
                original.getSize(),
                width,
                height,
                ownerUsername,
                "initial-upload",
                now);

        return asset.getId();
    }

    @Cacheable(
        cacheNames = "wallpaper-list",
        key = "(#keyword ?: '') + ':' + (#type ?: '') + ':' + (#sortBy ?: '') + ':' + #page + ':' + #pageSize",
        cacheManager = "wallpaperCacheManager",
        unless = "#result == null"
    )
    public List<Map<String, Object>> listPublished(String keyword, String type, String sortBy, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        int offset = (safePage - 1) * safeSize;
        return mapper.listPublished(safeText(keyword, 100), normalizeTypeAllowBlank(type), normalizeSort(sortBy), offset, safeSize);
    }

    @Cacheable(
        cacheNames = "wallpaper-detail",
        key = "#id",
        cacheManager = "wallpaperCacheManager",
        unless = "#result == null"
    )
    public Map<String, Object> detail(Long id) {
        return mapper.selectAssetById(id);
    }

    @Caching(evict = {
        @CacheEvict(cacheNames = "wallpaper-detail", key = "#id", cacheManager = "wallpaperCacheManager"),
        @CacheEvict(cacheNames = {"wallpaper-list", "wallpaper-admin-list"}, allEntries = true, cacheManager = "wallpaperCacheManager")
    })
    public boolean updateOwnerMetadata(Long id,
                                       String ownerUsername,
                                       String title,
                                       String description,
                                       String type,
                                       String tagsText) {
        return mapper.updateOwnerMetadata(id,
                ownerUsername,
                safeTitle(title),
                safeText(description, 2000),
                normalizeType(type),
                safeText(tagsText, 500),
                LocalDateTime.now()) > 0;
    }

    @Caching(evict = {
        @CacheEvict(cacheNames = "wallpaper-detail", key = "#id", cacheManager = "wallpaperCacheManager"),
        @CacheEvict(cacheNames = {"wallpaper-list", "wallpaper-admin-list"}, allEntries = true, cacheManager = "wallpaperCacheManager")
    })
    public boolean replaceOwnerSource(Long id,
                                      String ownerUsername,
                                      MultipartFile original,
                                      MultipartFile thumb320,
                                      MultipartFile thumb720,
                                      MultipartFile thumb1280,
                                      Integer width,
                                      Integer height,
                                      String reason) throws IOException {
        validateImageFile(original);
        validateImageFile(thumb320);
        validateImageFile(thumb720);
        validateImageFile(thumb1280);

        Map<String, Object> current = mapper.selectAssetById(id);
        if (current == null || current.isEmpty() || !Objects.equals(ownerUsername, current.get("ownerUsername"))) {
            return false;
        }

        String originalUrl = wallpaperR2StorageService.upload(original, "wallpapers/original");
        String thumb320Url = wallpaperR2StorageService.upload(thumb320, "wallpapers/thumb-320");
        String thumb720Url = wallpaperR2StorageService.upload(thumb720, "wallpapers/thumb-720");
        String thumb1280Url = wallpaperR2StorageService.upload(thumb1280, "wallpapers/thumb-1280");

        LocalDateTime now = LocalDateTime.now();
        int updated = mapper.replaceOwnerSource(id,
                ownerUsername,
                originalUrl,
                thumb320Url,
                thumb720Url,
                thumb1280Url,
                original.getSize(),
                width,
                height,
                now);
        if (updated <= 0) {
            return false;
        }

        int versionNo = parseInt(current.get("currentVersion"), 1) + 1;
        mapper.insertVersion(id,
                versionNo,
                originalUrl,
                thumb320Url,
                thumb720Url,
                thumb1280Url,
                original.getSize(),
                width,
                height,
                ownerUsername,
                safeText(reason, 300),
                now);
        return true;
    }

    @Caching(evict = {
        @CacheEvict(cacheNames = "wallpaper-detail", key = "#id", cacheManager = "wallpaperCacheManager"),
        @CacheEvict(cacheNames = {"wallpaper-list", "wallpaper-admin-list"}, allEntries = true, cacheManager = "wallpaperCacheManager")
    })
    public boolean deleteOwnerWallpaper(Long id, String ownerUsername) {
        return mapper.markOwnerDeleted(id, ownerUsername, LocalDateTime.now()) > 0;
    }

    @Caching(evict = {
        @CacheEvict(cacheNames = "wallpaper-detail", key = "#id", cacheManager = "wallpaperCacheManager"),
        @CacheEvict(cacheNames = {"wallpaper-list", "wallpaper-admin-list"}, allEntries = true, cacheManager = "wallpaperCacheManager")
    })
    public boolean apply(Long id, String username, String ip, String userAgent) {
        if (!checkRateLimit("wallpaper:apply:" + username, 60, 20)) {
            return false;
        }
        boolean firstTime = mapper.countApplyByUser(id, username) == 0;
        int updated = firstTime
                ? mapper.incrementApplyAndDownload(id)
                : mapper.incrementDownloadOnly(id);
        if (updated <= 0) {
            return false;
        }
        mapper.insertApplyLog(id,
                username,
                sha256Hex(ip),
                safeText(userAgent, 500),
                "apply",
                LocalDateTime.now());
        return true;
    }

    @Caching(evict = {
        @CacheEvict(cacheNames = "wallpaper-detail", key = "#id", cacheManager = "wallpaperCacheManager"),
        @CacheEvict(cacheNames = {"wallpaper-list", "wallpaper-admin-list"}, allEntries = true, cacheManager = "wallpaperCacheManager")
    })
    public boolean rate(Long id, String username, int score) {
        if (score < 1 || score > 5) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        mapper.upsertRating(id, username, score, now, now);
        mapper.recomputeRatingStats(id);
        return true;
    }

    public boolean report(Long id, String reporterUsername, String reasonType, String reasonDetail) {
        if (!checkRateLimit("wallpaper:report:" + reporterUsername, 3600, 20)) {
            return false;
        }
        return mapper.insertReport(id,
                reporterUsername,
                safeText(reasonType, 40),
                safeText(reasonDetail, 500),
                LocalDateTime.now()) > 0;
    }

    @Cacheable(
        cacheNames = "wallpaper-admin-list",
        key = "(#keyword ?: '') + ':' + (#type ?: '') + ':' + (#status ?: '') + ':' + #page + ':' + #pageSize",
        cacheManager = "wallpaperCacheManager",
        unless = "#result == null"
    )
    public List<Map<String, Object>> listAdmin(String keyword, String type, String status, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        int offset = (safePage - 1) * safeSize;
        return mapper.listAdmin(safeText(keyword, 100), normalizeTypeAllowBlank(type), safeText(status, 20), offset, safeSize);
    }

    @Caching(evict = {
        @CacheEvict(cacheNames = "wallpaper-detail", key = "#id", cacheManager = "wallpaperCacheManager"),
        @CacheEvict(cacheNames = {"wallpaper-list", "wallpaper-admin-list"}, allEntries = true, cacheManager = "wallpaperCacheManager")
    })
    public boolean adminUpdateMetadata(Long id,
                                       String title,
                                       String description,
                                       String type,
                                       String tagsText,
                                       String status) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime publishedAt = "published".equalsIgnoreCase(status) ? now : null;
        return mapper.updateAdminMetadata(id,
                safeTitle(title),
                safeText(description, 2000),
                normalizeType(type),
                safeText(tagsText, 500),
                safeText(status, 20),
                now,
                publishedAt) > 0;
    }

    @Caching(evict = {
        @CacheEvict(cacheNames = "wallpaper-detail", key = "#id", cacheManager = "wallpaperCacheManager"),
        @CacheEvict(cacheNames = {"wallpaper-list", "wallpaper-admin-list"}, allEntries = true, cacheManager = "wallpaperCacheManager")
    })
    public boolean adminReview(Long id, String reviewerName, String action, String reason) {
        String normalizedAction = safeText(action, 30);
        String nextStatus;
        if ("approve".equalsIgnoreCase(normalizedAction) || "publish".equalsIgnoreCase(normalizedAction)) {
            nextStatus = "published";
        } else if ("reject".equalsIgnoreCase(normalizedAction)) {
            nextStatus = "rejected";
        } else if ("delist".equalsIgnoreCase(normalizedAction)) {
            nextStatus = "delisted";
        } else if ("relist".equalsIgnoreCase(normalizedAction)) {
            nextStatus = "published";
        } else {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime publishedAt = "published".equals(nextStatus) ? now : null;
        int updated = mapper.updateStatusByAdmin(id, nextStatus, now, publishedAt);
        if (updated <= 0) {
            return false;
        }
        mapper.insertReviewLog(id, normalizedAction, reviewerName, safeText(reason, 500), now);
        return true;
    }

    public List<Map<String, Object>> listReports(String status, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        int offset = (safePage - 1) * safeSize;
        return mapper.listReports(safeText(status, 20), offset, safeSize);
    }

    public boolean resolveReport(Long id, String resolverName, String status, String resolutionNote) {
        return mapper.resolveReport(id,
                safeText(status, 20),
                resolverName,
                safeText(resolutionNote, 500),
                LocalDateTime.now()) > 0;
    }

    public List<Map<String, Object>> listRatings(Long wallpaperId, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        int offset = (safePage - 1) * safeSize;
        return mapper.listRatings(wallpaperId, offset, safeSize);
    }

    @Caching(evict = {
        @CacheEvict(cacheNames = "wallpaper-detail", key = "#wallpaperId", cacheManager = "wallpaperCacheManager"),
        @CacheEvict(cacheNames = {"wallpaper-list", "wallpaper-admin-list"}, allEntries = true, cacheManager = "wallpaperCacheManager")
    })
    public boolean adminDeleteRating(Long ratingId, Long wallpaperId) {
        int deleted = mapper.deleteRatingById(ratingId);
        if (deleted <= 0) {
            return false;
        }
        mapper.recomputeRatingStats(wallpaperId);
        return true;
    }

    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new IllegalArgumentException("图片大小不能超过 20MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("仅支持图片文件");
        }
    }

    private boolean checkRateLimit(String key, int windowSeconds, int maxCount) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
        }
        return count != null && count <= maxCount;
    }

    private String safeTitle(String value) {
        String normalized = safeText(value, 120);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("标题不能为空");
        }
        return normalized;
    }

    private String normalizeType(String type) {
        String normalized = safeText(type, 20);
        if (normalized.isBlank()) {
            return "image";
        }
        if (!"image".equalsIgnoreCase(normalized) && !"video".equalsIgnoreCase(normalized)) {
            throw new IllegalArgumentException("type 仅支持 image/video");
        }
        return normalized.toLowerCase();
    }

    private String normalizeTypeAllowBlank(String type) {
        String normalized = safeText(type, 20);
        if (normalized.isBlank()) {
            return "";
        }
        return normalizeType(normalized);
    }

    private String normalizeSort(String sortBy) {
        String normalized = safeText(sortBy, 20);
        if ("rating".equalsIgnoreCase(normalized)) {
            return "rating";
        }
        if ("apply".equalsIgnoreCase(normalized)) {
            return "apply";
        }
        return "newest";
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

    private int parseInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private String sha256Hex(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return value;
        }
    }
}
