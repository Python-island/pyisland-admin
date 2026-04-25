package com.pyisland.server.user.service;

import com.pyisland.server.upload.service.ObjectReplicationTaskService;
import com.pyisland.server.upload.service.StorageUploadResult;
import com.pyisland.server.upload.service.WallpaperR2StorageService;
import com.pyisland.server.user.entity.WallpaperAsset;
import com.pyisland.server.user.mapper.WallpaperMarketMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 壁纸市场服务。
 */
@Service
public class WallpaperMarketService {

    private static final Logger log = LoggerFactory.getLogger(WallpaperMarketService.class);

    private static final long MAX_IMAGE_SIZE = 20L * 1024 * 1024;
    private static final long MAX_VIDEO_SIZE = 100L * 1024 * 1024;

    private final WallpaperMarketMapper mapper;
    private final WallpaperR2StorageService wallpaperR2StorageService;
    private final ObjectReplicationTaskService objectReplicationTaskService;
    private final StringRedisTemplate uploadRateRedisTemplate;
    private final WallpaperTagService tagService;
    private final WallpaperDetailBloomService wallpaperDetailBloomService;
    private final StaticAssetUrlService staticAssetUrlService;

    public WallpaperMarketService(WallpaperMarketMapper mapper,
                                  WallpaperR2StorageService wallpaperR2StorageService,
                                  ObjectReplicationTaskService objectReplicationTaskService,
                                  @Qualifier("uploadRateRedisTemplate") StringRedisTemplate uploadRateRedisTemplate,
                                  WallpaperTagService tagService,
                                  WallpaperDetailBloomService wallpaperDetailBloomService,
                                  StaticAssetUrlService staticAssetUrlService) {
        this.mapper = mapper;
        this.wallpaperR2StorageService = wallpaperR2StorageService;
        this.objectReplicationTaskService = objectReplicationTaskService;
        this.uploadRateRedisTemplate = uploadRateRedisTemplate;
        this.tagService = tagService;
        this.wallpaperDetailBloomService = wallpaperDetailBloomService;
        this.staticAssetUrlService = staticAssetUrlService;
        rebuildWallpaperDetailBloom();
    }

    private void rebuildWallpaperDetailBloom() {
        wallpaperDetailBloomService.rebuildFromIds(mapper.listActiveAssetIds());
    }

    @CacheEvict(cacheNames = {"wallpaper-list", "wallpaper-admin-list", "wallpaper-my-list"}, allEntries = true, cacheManager = "wallpaperCacheManager")
    public Long create(String ownerUsername,
                       String title,
                       String description,
                       String type,
                       String tagsText,
                       boolean copyrightDeclared,
                       String copyrightInfo,
                       MultipartFile original,
                       MultipartFile thumb320,
                       MultipartFile thumb720,
                       MultipartFile thumb1280,
                       Integer width,
                       Integer height,
                       Long durationMs,
                       BigDecimal frameRate) throws IOException {
        String normalizedType = normalizeType(type);
        if ("video".equals(normalizedType)) {
            validateVideoFile(original);
        } else {
            validateImageFile(original);
        }
        validateImageFile(thumb320);
        validateImageFile(thumb720);
        validateImageFile(thumb1280);

        StorageUploadResult originalUploadResult = wallpaperR2StorageService.uploadObject(original, "wallpapers/original");
        StorageUploadResult thumb320UploadResult = wallpaperR2StorageService.uploadObject(thumb320, "wallpapers/thumb-320");
        StorageUploadResult thumb720UploadResult = wallpaperR2StorageService.uploadObject(thumb720, "wallpapers/thumb-720");
        StorageUploadResult thumb1280UploadResult = wallpaperR2StorageService.uploadObject(thumb1280, "wallpapers/thumb-1280");

        String originalUrl = originalUploadResult.publicUrl();
        String thumb320Url = thumb320UploadResult.publicUrl();
        String thumb720Url = thumb720UploadResult.publicUrl();
        String thumb1280Url = thumb1280UploadResult.publicUrl();

        LocalDateTime now = LocalDateTime.now();
        WallpaperAsset asset = new WallpaperAsset();
        asset.setOwnerUsername(ownerUsername);
        asset.setTitle(safeTitle(title));
        asset.setDescription(safeText(description, 2000));
        asset.setType(normalizedType);
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
        asset.setCopyrightInfo(copyrightDeclared ? safeText(copyrightInfo, 500) : "");
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

        if ("video".equals(normalizedType)) {
            mapper.upsertVideoMeta(asset.getId(),
                    safeDurationMs(durationMs),
                    safeFrameRate(frameRate),
                    now,
                    now);
        }

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

        enqueueReplicationSafely(asset.getId(), ownerUsername, "originalUrl", originalUploadResult, 3);
        enqueueReplicationSafely(asset.getId(), ownerUsername, "thumb320Url", thumb320UploadResult, 3);
        enqueueReplicationSafely(asset.getId(), ownerUsername, "thumb720Url", thumb720UploadResult, 3);
        enqueueReplicationSafely(asset.getId(), ownerUsername, "thumb1280Url", thumb1280UploadResult, 3);

        tagService.syncTagsForWallpaper(asset.getId(), asset.getTagsText(), ownerUsername);
        wallpaperDetailBloomService.add(asset.getId());

        return asset.getId();
    }

    public boolean allowUpload(String ownerUsername) {
        return checkRateLimit(uploadRateRedisTemplate, "wallpaper:upload:" + ownerUsername, 3600, 5);
    }

    @Cacheable(
        cacheNames = "wallpaper-list",
        key = "(#keyword ?: '') + ':' + (#type ?: '') + ':' + (#sortBy ?: '') + ':' + #page + ':' + #pageSize + ':' + (#requestedNode ?: '') + ':' + #proUser",
        cacheManager = "wallpaperCacheManager",
        unless = "#result == null"
    )
    public List<Map<String, Object>> listPublished(String keyword,
                                                   String type,
                                                   String sortBy,
                                                   int page,
                                                   int pageSize,
                                                   String requestedNode,
                                                   boolean proUser) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        int offset = (safePage - 1) * safeSize;
        List<Map<String, Object>> rows = mapper.listPublished(safeText(keyword, 100), normalizeTypeAllowBlank(type), normalizeSort(sortBy), offset, safeSize);
        return rewriteAssetRows(rows, requestedNode, proUser);
    }

    @Cacheable(
        cacheNames = "wallpaper-list",
        key = "'total:' + (#keyword ?: '') + ':' + (#type ?: '')",
        cacheManager = "wallpaperCacheManager",
        unless = "#result == null"
    )
    public long countPublished(String keyword, String type) {
        return mapper.countPublished(safeText(keyword, 100), normalizeTypeAllowBlank(type));
    }

    @Cacheable(
        cacheNames = "wallpaper-my-list",
        key = "#ownerUsername + ':' + (#keyword ?: '') + ':' + (#type ?: '') + ':' + (#sortBy ?: '') + ':' + #page + ':' + #pageSize + ':' + (#requestedNode ?: '') + ':' + #proUser",
        cacheManager = "wallpaperCacheManager",
        unless = "#result == null"
    )
    public List<Map<String, Object>> listOwn(String ownerUsername,
                                             String keyword,
                                             String type,
                                             String sortBy,
                                             int page,
                                             int pageSize,
                                             String requestedNode,
                                             boolean proUser) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        int offset = (safePage - 1) * safeSize;
        List<Map<String, Object>> rows = mapper.listMine(ownerUsername,
                safeText(keyword, 100),
                normalizeTypeAllowBlank(type),
                normalizeSort(sortBy),
                offset,
                safeSize);
        return rewriteAssetRows(rows, requestedNode, proUser);
    }

    @Cacheable(
        cacheNames = "wallpaper-my-list",
        key = "'total:' + #ownerUsername + ':' + (#keyword ?: '') + ':' + (#type ?: '')",
        cacheManager = "wallpaperCacheManager",
        unless = "#result == null"
    )
    public long countOwn(String ownerUsername, String keyword, String type) {
        return mapper.countMine(ownerUsername,
                safeText(keyword, 100),
                normalizeTypeAllowBlank(type));
    }

    @Cacheable(
        cacheNames = "wallpaper-detail",
        key = "#id + ':' + (#requestedNode ?: '') + ':' + #proUser",
        cacheManager = "wallpaperCacheManager",
        unless = "#result == null"
    )
    public Map<String, Object> detail(Long id, String requestedNode, boolean proUser) {
        if (id == null || id <= 0) {
            return null;
        }
        if (!wallpaperDetailBloomService.mightContain(id)) {
            return null;
        }
        Map<String, Object> row = mapper.selectAssetById(id);
        return rewriteAssetRow(row, requestedNode, proUser);
    }

    @Caching(evict = {
        @CacheEvict(cacheNames = "wallpaper-detail", key = "#id", cacheManager = "wallpaperCacheManager"),
        @CacheEvict(cacheNames = {"wallpaper-list", "wallpaper-admin-list", "wallpaper-my-list"}, allEntries = true, cacheManager = "wallpaperCacheManager")
    })
    public boolean updateOwnerMetadata(Long id,
                                       String ownerUsername,
                                       String title,
                                       String description,
                                       String type,
                                       String tagsText,
                                       String copyrightInfo) {
        String safeTags = safeText(tagsText, 500);
        String normalizedType = normalizeType(type);
        int updated = mapper.updateOwnerMetadata(id,
                ownerUsername,
                safeTitle(title),
                safeText(description, 2000),
                normalizedType,
                safeTags,
                safeText(copyrightInfo, 500),
                LocalDateTime.now());
        if (updated <= 0) {
            return false;
        }
        if (!"video".equals(normalizedType)) {
            mapper.deleteVideoMetaByWallpaperId(id);
        }
        tagService.syncTagsForWallpaper(id, safeTags, ownerUsername);
        return true;
    }

    @Caching(evict = {
        @CacheEvict(cacheNames = "wallpaper-detail", key = "#id", cacheManager = "wallpaperCacheManager"),
        @CacheEvict(cacheNames = {"wallpaper-list", "wallpaper-admin-list", "wallpaper-my-list"}, allEntries = true, cacheManager = "wallpaperCacheManager")
    })
    public boolean replaceOwnerSource(Long id,
                                      String ownerUsername,
                                      MultipartFile original,
                                      MultipartFile thumb320,
                                      MultipartFile thumb720,
                                      MultipartFile thumb1280,
                                      Integer width,
                                      Integer height,
                                      Long durationMs,
                                      BigDecimal frameRate,
                                      String reason) throws IOException {
        validateImageFile(thumb320);
        validateImageFile(thumb720);
        validateImageFile(thumb1280);

        Map<String, Object> current = mapper.selectAssetById(id);
        if (current == null || current.isEmpty() || !Objects.equals(ownerUsername, current.get("ownerUsername"))) {
            return false;
        }
        String currentTypeRaw = current.get("type") == null ? "image" : String.valueOf(current.get("type"));
        String currentType = normalizeType(currentTypeRaw);
        if ("video".equals(currentType)) {
            validateVideoFile(original);
        } else {
            validateImageFile(original);
        }

        StorageUploadResult originalUploadResult = wallpaperR2StorageService.uploadObject(original, "wallpapers/original");
        StorageUploadResult thumb320UploadResult = wallpaperR2StorageService.uploadObject(thumb320, "wallpapers/thumb-320");
        StorageUploadResult thumb720UploadResult = wallpaperR2StorageService.uploadObject(thumb720, "wallpapers/thumb-720");
        StorageUploadResult thumb1280UploadResult = wallpaperR2StorageService.uploadObject(thumb1280, "wallpapers/thumb-1280");

        String originalUrl = originalUploadResult.publicUrl();
        String thumb320Url = thumb320UploadResult.publicUrl();
        String thumb720Url = thumb720UploadResult.publicUrl();
        String thumb1280Url = thumb1280UploadResult.publicUrl();

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

        if ("video".equals(currentType)) {
            mapper.upsertVideoMeta(id,
                    safeDurationMs(durationMs),
                    safeFrameRate(frameRate),
                    now,
                    now);
        } else {
            mapper.deleteVideoMetaByWallpaperId(id);
        }

        enqueueReplicationSafely(id, ownerUsername, "originalUrl", originalUploadResult, 2);
        enqueueReplicationSafely(id, ownerUsername, "thumb320Url", thumb320UploadResult, 2);
        enqueueReplicationSafely(id, ownerUsername, "thumb720Url", thumb720UploadResult, 2);
        enqueueReplicationSafely(id, ownerUsername, "thumb1280Url", thumb1280UploadResult, 2);

        return true;
    }

    @Caching(evict = {
        @CacheEvict(cacheNames = "wallpaper-detail", key = "#id", cacheManager = "wallpaperCacheManager"),
        @CacheEvict(cacheNames = {"wallpaper-list", "wallpaper-admin-list", "wallpaper-my-list"}, allEntries = true, cacheManager = "wallpaperCacheManager")
    })
    public boolean deleteOwnerWallpaper(Long id, String ownerUsername) {
        Map<String, Object> current = mapper.selectAssetById(id);
        if (current == null || current.isEmpty() || !Objects.equals(ownerUsername, current.get("ownerUsername"))) {
            return false;
        }
        boolean removed = mapper.markOwnerDeleted(id, ownerUsername, LocalDateTime.now()) > 0;
        if (removed) {
            mapper.deleteVideoMetaByWallpaperId(id);
            tagService.clearWallpaperTags(id);
            purgeR2Assets(id, current);
            wallpaperDetailBloomService.remove(id);
        }
        return removed;
    }

    @Caching(evict = {
        @CacheEvict(cacheNames = "wallpaper-detail", key = "#id", cacheManager = "wallpaperCacheManager"),
        @CacheEvict(cacheNames = {"wallpaper-list", "wallpaper-admin-list", "wallpaper-my-list"}, allEntries = true, cacheManager = "wallpaperCacheManager")
    })
    public boolean adminDeleteWallpaper(Long id) {
        Map<String, Object> current = mapper.selectAssetById(id);
        if (current == null || current.isEmpty()) {
            return false;
        }
        boolean removed = mapper.markAdminDeleted(id, LocalDateTime.now()) > 0;
        if (removed) {
            mapper.deleteVideoMetaByWallpaperId(id);
            tagService.clearWallpaperTags(id);
            purgeR2Assets(id, current);
            wallpaperDetailBloomService.remove(id);
        }
        return removed;
    }

    /**
     * 清理壁纸当前与所有历史版本在 R2 上的对象；失败不回滚数据库软删除，仅打警告。
     */
    private void purgeR2Assets(Long id, Map<String, Object> current) {
        Set<String> urls = new LinkedHashSet<>();
        collectUrl(urls, current.get("originalUrl"));
        collectUrl(urls, current.get("thumb320Url"));
        collectUrl(urls, current.get("thumb720Url"));
        collectUrl(urls, current.get("thumb1280Url"));
        try {
            List<Map<String, Object>> versions = mapper.listVersionAssetUrls(id);
            if (versions != null) {
                for (Map<String, Object> row : versions) {
                    collectUrl(urls, row.get("originalUrl"));
                    collectUrl(urls, row.get("thumb320Url"));
                    collectUrl(urls, row.get("thumb720Url"));
                    collectUrl(urls, row.get("thumb1280Url"));
                }
            }
        } catch (Exception ignored) {
            // ignore version lookup failure; fall back to current-asset purge only
        }
        if (!urls.isEmpty()) {
            wallpaperR2StorageService.deleteAll(urls);
        }
    }

    private List<Map<String, Object>> rewriteAssetRows(List<Map<String, Object>> rows,
                                                        String requestedNode,
                                                        boolean proUser) {
        if (rows == null || rows.isEmpty()) {
            return rows;
        }
        for (Map<String, Object> row : rows) {
            rewriteAssetRow(row, requestedNode, proUser);
        }
        return rows;
    }

    private Map<String, Object> rewriteAssetRow(Map<String, Object> row,
                                                 String requestedNode,
                                                 boolean proUser) {
        if (row == null || row.isEmpty()) {
            return row;
        }
        rewriteMapUrl(row, "ownerAvatar", requestedNode, proUser);
        rewriteMapUrl(row, "originalUrl", requestedNode, proUser);
        rewriteMapUrl(row, "thumb320Url", requestedNode, proUser);
        rewriteMapUrl(row, "thumb720Url", requestedNode, proUser);
        rewriteMapUrl(row, "thumb1280Url", requestedNode, proUser);
        return row;
    }

    private void rewriteMapUrl(Map<String, Object> row,
                               String field,
                               String requestedNode,
                               boolean proUser) {
        Object value = row.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            return;
        }
        row.put(field, staticAssetUrlService.rewriteUrl(text, requestedNode, proUser));
    }

    private void collectUrl(Set<String> bag, Object value) {
        if (value == null) return;
        String s = value.toString().trim();
        if (!s.isEmpty()) bag.add(s);
    }

    private void enqueueReplicationSafely(Long wallpaperId,
                                          String ownerUsername,
                                          String fieldName,
                                          StorageUploadResult uploadResult,
                                          int priority) {
        if (uploadResult == null) {
            return;
        }
        try {
            objectReplicationTaskService.enqueueForOtherProviders(
                    "wallpaper_asset",
                    wallpaperId,
                    ownerUsername,
                    fieldName,
                    uploadResult,
                    priority
            );
        } catch (Exception ex) {
            log.warn("enqueue wallpaper replication task failed id={} field={} reason={}",
                    wallpaperId,
                    fieldName,
                    ex.getMessage());
        }
    }

    @Caching(evict = {
        @CacheEvict(cacheNames = "wallpaper-detail", key = "#id", cacheManager = "wallpaperCacheManager"),
        @CacheEvict(cacheNames = {"wallpaper-list", "wallpaper-admin-list", "wallpaper-my-list"}, allEntries = true, cacheManager = "wallpaperCacheManager")
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
        @CacheEvict(cacheNames = {"wallpaper-list", "wallpaper-admin-list", "wallpaper-my-list"}, allEntries = true, cacheManager = "wallpaperCacheManager")
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
        if (mapper.countPendingReportByUser(id, reporterUsername) > 0) {
            return false;
        }
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
        @CacheEvict(cacheNames = {"wallpaper-list", "wallpaper-admin-list", "wallpaper-my-list"}, allEntries = true, cacheManager = "wallpaperCacheManager")
    })
    public boolean adminUpdateMetadata(Long id,
                                       String title,
                                       String description,
                                       String type,
                                       String tagsText,
                                       String copyrightInfo,
                                       String status) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime publishedAt = "published".equalsIgnoreCase(status) ? now : null;
        String safeTags = safeText(tagsText, 500);
        String normalizedType = normalizeType(type);
        int updated = mapper.updateAdminMetadata(id,
                safeTitle(title),
                safeText(description, 2000),
                normalizedType,
                safeTags,
                safeText(copyrightInfo, 500),
                safeText(status, 20),
                now,
                publishedAt);
        if (updated <= 0) {
            return false;
        }
        if (!"video".equals(normalizedType)) {
            mapper.deleteVideoMetaByWallpaperId(id);
        }
        Map<String, Object> current = mapper.selectAssetById(id);
        String ownerUsername = current == null ? null : (String) current.get("ownerUsername");
        tagService.syncTagsForWallpaper(id, safeTags, ownerUsername);
        return true;
    }

    @Caching(evict = {
        @CacheEvict(cacheNames = "wallpaper-detail", key = "#id", cacheManager = "wallpaperCacheManager"),
        @CacheEvict(cacheNames = {"wallpaper-list", "wallpaper-admin-list", "wallpaper-my-list"}, allEntries = true, cacheManager = "wallpaperCacheManager")
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
        @CacheEvict(cacheNames = {"wallpaper-list", "wallpaper-admin-list", "wallpaper-my-list"}, allEntries = true, cacheManager = "wallpaperCacheManager")
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

    private void validateVideoFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        if (file.getSize() > MAX_VIDEO_SIZE) {
            throw new IllegalArgumentException("视频大小不能超过 100MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("video/")) {
            throw new IllegalArgumentException("仅支持视频文件");
        }
        String filename = file.getOriginalFilename();
        String lower = filename == null ? "" : filename.toLowerCase();
        if (!lower.endsWith(".mp4") && !lower.endsWith(".mov")) {
            throw new IllegalArgumentException("仅支持 mp4/mov 视频文件");
        }
    }

    private Long safeDurationMs(Long durationMs) {
        if (durationMs == null || durationMs <= 0) {
            return null;
        }
        return durationMs;
    }

    private BigDecimal safeFrameRate(BigDecimal frameRate) {
        if (frameRate == null) {
            return null;
        }
        if (frameRate.signum() <= 0) {
            return null;
        }
        if (frameRate.compareTo(new BigDecimal("240")) > 0) {
            return new BigDecimal("240");
        }
        return frameRate;
    }

    private boolean checkRateLimit(String key, int windowSeconds, int maxCount) {
        return checkRateLimit(uploadRateRedisTemplate, key, windowSeconds, maxCount);
    }

    private boolean checkRateLimit(StringRedisTemplate template, String key, int windowSeconds, int maxCount) {
        Long count = template.opsForValue().increment(key);
        if (count != null && count == 1L) {
            template.expire(key, windowSeconds, TimeUnit.SECONDS);
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
