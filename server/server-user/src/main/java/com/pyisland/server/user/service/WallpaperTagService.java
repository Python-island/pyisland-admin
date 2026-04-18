package com.pyisland.server.user.service;

import com.pyisland.server.user.entity.WallpaperTag;
import com.pyisland.server.user.mapper.WallpaperTagMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 壁纸标签服务。
 */
@Service
public class WallpaperTagService {

    private static final int MAX_TAG_LENGTH = 30;
    private static final int MAX_TAG_COUNT_PER_WALLPAPER = 10;

    private final WallpaperTagMapper mapper;

    public WallpaperTagService(WallpaperTagMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 将壁纸 tagsText 解析并同步到 wallpaper_tag 与 wallpaper_tag_ref。
     * 新标签自动创建；已存在的标签复用；usage_count 按最终引用数重算。
     *
     * @param wallpaperId   壁纸 ID。
     * @param tagsText      逗号分隔的标签文本。
     * @param creator       创建者用户名（仅用于新标签）。
     * @return 最终生效的规范化 tagsText（去重去空白、保持用户输入顺序）。
     */
    public String syncTagsForWallpaper(Long wallpaperId, String tagsText, String creator) {
        Set<Long> previousTagIds = new HashSet<>(mapper.listTagIdsByWallpaper(wallpaperId));
        mapper.deleteRefsByWallpaper(wallpaperId);

        List<String> normalized = normalizeTagList(tagsText);
        LocalDateTime now = LocalDateTime.now();
        Set<Long> newTagIds = new LinkedHashSet<>();

        for (String name : normalized) {
            String slug = toSlug(name);
            if (slug.isEmpty()) continue;
            WallpaperTag tag = mapper.selectBySlug(slug);
            if (tag == null) {
                tag = new WallpaperTag();
                tag.setName(name);
                tag.setSlug(slug);
                tag.setCreatorUsername(creator);
                tag.setEnabled(Boolean.TRUE);
                tag.setUsageCount(0);
                tag.setCreatedAt(now);
                tag.setUpdatedAt(now);
                mapper.insertTag(tag);
            }
            if (tag.getId() == null) continue;
            mapper.insertRef(wallpaperId, tag.getId(), now);
            newTagIds.add(tag.getId());
        }

        Set<Long> affected = new HashSet<>(previousTagIds);
        affected.addAll(newTagIds);
        for (Long tagId : affected) {
            mapper.recomputeUsageCount(tagId);
        }

        return String.join(",", normalized);
    }

    /**
     * 壁纸被删除时，移除其所有 tag 引用并重算受影响 tag 的 usage_count。
     *
     * @param wallpaperId 壁纸 ID。
     */
    public void clearWallpaperTags(Long wallpaperId) {
        Set<Long> previousTagIds = new HashSet<>(mapper.listTagIdsByWallpaper(wallpaperId));
        mapper.deleteRefsByWallpaper(wallpaperId);
        for (Long tagId : previousTagIds) {
            mapper.recomputeUsageCount(tagId);
        }
    }

    public List<Map<String, Object>> search(String keyword, int limit) {
        int safeLimit = Math.min(50, Math.max(1, limit));
        String safeKeyword = keyword == null ? null : keyword.trim();
        return mapper.searchByKeyword(safeKeyword, safeLimit);
    }

    public List<Map<String, Object>> listAdmin(String keyword, Integer enabled, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        int offset = (safePage - 1) * safeSize;
        return mapper.listAdmin(keyword == null ? null : keyword.trim(), enabled, offset, safeSize);
    }

    public long countAdmin(String keyword, Integer enabled) {
        return mapper.countAdmin(keyword == null ? null : keyword.trim(), enabled);
    }

    public boolean updateName(Long id, String name) {
        String safeName = sanitizeTagName(name);
        if (safeName.isEmpty()) return false;
        String slug = toSlug(safeName);
        if (slug.isEmpty()) return false;
        WallpaperTag existing = mapper.selectBySlug(slug);
        if (existing != null && !existing.getId().equals(id)) {
            return false;
        }
        return mapper.updateName(id, safeName, slug, LocalDateTime.now()) > 0;
    }

    public boolean setEnabled(Long id, boolean enabled) {
        return mapper.updateEnabled(id, enabled, LocalDateTime.now()) > 0;
    }

    public boolean deleteTag(Long id) {
        mapper.deleteRefsByTag(id);
        return mapper.deleteTag(id) > 0;
    }

    public List<Map<String, Object>> listTagsByWallpaper(Long wallpaperId) {
        return mapper.listTagsByWallpaper(wallpaperId);
    }

    private List<String> normalizeTagList(String tagsText) {
        if (tagsText == null || tagsText.isBlank()) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        Set<String> seenSlugs = new HashSet<>();
        for (String raw : Arrays.asList(tagsText.split("[,，]"))) {
            String name = sanitizeTagName(raw);
            if (name.isEmpty()) continue;
            String slug = toSlug(name);
            if (slug.isEmpty() || !seenSlugs.add(slug)) continue;
            result.add(name);
            if (result.size() >= MAX_TAG_COUNT_PER_WALLPAPER) break;
        }
        return result;
    }

    private String sanitizeTagName(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        if (trimmed.length() > MAX_TAG_LENGTH) {
            trimmed = trimmed.substring(0, MAX_TAG_LENGTH);
        }
        return trimmed;
    }

    /**
     * 根据原始标签名生成小写、去空白的唯一 slug，用于去重。
     * @param name 原始标签名。
     * @return 归一化 slug。
     */
    private String toSlug(String name) {
        if (name == null) return "";
        String lower = name.trim().toLowerCase();
        StringBuilder sb = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (Character.isWhitespace(c)) continue;
            sb.append(c);
        }
        String slug = sb.toString();
        if (slug.length() > MAX_TAG_LENGTH) {
            slug = slug.substring(0, MAX_TAG_LENGTH);
        }
        return slug;
    }
}
