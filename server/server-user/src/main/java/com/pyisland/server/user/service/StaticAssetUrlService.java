package com.pyisland.server.user.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 静态资源 URL 节点解析服务。
 */
@Service
public class StaticAssetUrlService {

    public static final String NODE_HEADER_NAME = "X-Static-Asset-Node";

    private static final String RESOURCE_AVATAR = "avatar";
    private static final String RESOURCE_WALLPAPER = "wallpaper";
    private static final String RESOURCE_FEEDBACK = "feedback";
    private static final String RESOURCE_ADMIN_AVATAR = "admin-avatar";

    @Value("${cloudflare.r2.endpoint:}")
    private String r2AvatarEndpoint;

    @Value("${cloudflare.r2.bucket-name:}")
    private String r2AvatarBucketName;

    @Value("${cloudflare.r2.public-domain:}")
    private String r2AvatarDomain;

    @Value("${cloudflare.wallpaper-r2.endpoint:}")
    private String r2WallpaperEndpoint;

    @Value("${cloudflare.wallpaper-r2.bucket-name:}")
    private String r2WallpaperBucketName;

    @Value("${cloudflare.wallpaper-r2.public-domain:}")
    private String r2WallpaperDomain;

    @Value("${cloudflare.feedback-r2.endpoint:}")
    private String r2FeedbackEndpoint;

    @Value("${cloudflare.feedback-r2.bucket-name:}")
    private String r2FeedbackBucketName;

    @Value("${cloudflare.feedback-r2.public-domain:}")
    private String r2FeedbackDomain;

    @Value("${aliyun.oss.admin-avatar.endpoint:}")
    private String ossAdminAvatarEndpoint;

    @Value("${aliyun.oss.admin-avatar.bucket-name:}")
    private String ossAdminAvatarBucketName;

    @Value("${aliyun.oss.admin-avatar.domain:}")
    private String ossAdminAvatarDomain;

    @Value("${aliyun.oss.avatar.endpoint:}")
    private String ossAvatarEndpoint;

    @Value("${aliyun.oss.avatar.bucket-name:}")
    private String ossAvatarBucketName;

    @Value("${aliyun.oss.avatar.domain:}")
    private String ossAvatarDomain;

    @Value("${aliyun.oss.wallpaper.endpoint:}")
    private String ossWallpaperEndpoint;

    @Value("${aliyun.oss.wallpaper.bucket-name:}")
    private String ossWallpaperBucketName;

    @Value("${aliyun.oss.wallpaper.domain:}")
    private String ossWallpaperDomain;

    @Value("${aliyun.oss.feedback.endpoint:}")
    private String ossFeedbackEndpoint;

    @Value("${aliyun.oss.feedback.bucket-name:}")
    private String ossFeedbackBucketName;

    @Value("${aliyun.oss.feedback.domain:}")
    private String ossFeedbackDomain;

    @Value("${tencent.cos.avatar.region:}")
    private String cosAvatarRegion;

    @Value("${tencent.cos.avatar.bucket-name:}")
    private String cosAvatarBucketName;

    @Value("${tencent.cos.avatar.domain:}")
    private String cosAvatarDomain;

    @Value("${tencent.cos.wallpaper.region:}")
    private String cosWallpaperRegion;

    @Value("${tencent.cos.wallpaper.bucket-name:}")
    private String cosWallpaperBucketName;

    @Value("${tencent.cos.wallpaper.domain:}")
    private String cosWallpaperDomain;

    @Value("${tencent.cos.feedback.region:}")
    private String cosFeedbackRegion;

    @Value("${tencent.cos.feedback.bucket-name:}")
    private String cosFeedbackBucketName;

    @Value("${tencent.cos.feedback.domain:}")
    private String cosFeedbackDomain;

    public String rewriteUrl(String url, String requestedNodeRaw, boolean proUser) {
        if (url == null || url.isBlank()) {
            return url;
        }
        AssetNode targetNode = resolvePreferredNode(requestedNodeRaw, proUser);
        ParsedAsset parsed = parseAsset(url);
        if (parsed == null) {
            return url;
        }
        String normalizedResource = normalizeResourceForNode(parsed.resourceType(), targetNode);
        String targetUrl = buildPublicUrl(targetNode, normalizedResource, parsed.objectKey());
        if (targetUrl != null && !targetUrl.isBlank()) {
            return targetUrl;
        }
        if (targetNode != AssetNode.R2) {
            String fallback = buildPublicUrl(AssetNode.R2, normalizeResourceForNode(parsed.resourceType(), AssetNode.R2), parsed.objectKey());
            if (fallback != null && !fallback.isBlank()) {
                return fallback;
            }
        }
        return url;
    }

    public AssetNode resolvePreferredNode(String requestedNodeRaw, boolean proUser) {
        if (!proUser) {
            return AssetNode.R2;
        }
        AssetNode requested = AssetNode.fromRaw(requestedNodeRaw);
        if (requested == AssetNode.COS || requested == AssetNode.OSS || requested == AssetNode.R2) {
            return requested;
        }
        return AssetNode.R2;
    }

    private ParsedAsset parseAsset(String url) {
        for (SourcePrefix prefix : allSourcePrefixes()) {
            String objectKey = stripPrefix(url, prefix.prefix());
            if (objectKey == null || objectKey.isBlank()) {
                continue;
            }
            return new ParsedAsset(prefix.resourceType(), objectKey);
        }
        String inferredKey = inferObjectKeyByFolder(url);
        if (inferredKey == null || inferredKey.isBlank()) {
            return null;
        }
        return new ParsedAsset(resolveResourceTypeByKey(inferredKey), inferredKey);
    }

    private List<SourcePrefix> allSourcePrefixes() {
        List<SourcePrefix> prefixes = new ArrayList<>();
        addResourcePrefixes(prefixes, RESOURCE_AVATAR, r2AvatarDomain, r2AvatarEndpoint, r2AvatarBucketName);
        addResourcePrefixes(prefixes, RESOURCE_WALLPAPER, r2WallpaperDomain, r2WallpaperEndpoint, r2WallpaperBucketName);
        addResourcePrefixes(prefixes, RESOURCE_FEEDBACK, r2FeedbackDomain, r2FeedbackEndpoint, r2FeedbackBucketName);
        addResourcePrefixes(prefixes, RESOURCE_ADMIN_AVATAR, ossAdminAvatarDomain, ossAdminAvatarEndpoint, ossAdminAvatarBucketName);
        addResourcePrefixes(prefixes, RESOURCE_AVATAR, ossAvatarDomain, ossAvatarEndpoint, ossAvatarBucketName);
        addResourcePrefixes(prefixes, RESOURCE_WALLPAPER, ossWallpaperDomain, ossWallpaperEndpoint, ossWallpaperBucketName);
        addResourcePrefixes(prefixes, RESOURCE_FEEDBACK, ossFeedbackDomain, ossFeedbackEndpoint, ossFeedbackBucketName);
        addCosPrefixes(prefixes, RESOURCE_AVATAR, cosAvatarDomain, cosAvatarBucketName, cosAvatarRegion);
        addCosPrefixes(prefixes, RESOURCE_WALLPAPER, cosWallpaperDomain, cosWallpaperBucketName, cosWallpaperRegion);
        addCosPrefixes(prefixes, RESOURCE_FEEDBACK, cosFeedbackDomain, cosFeedbackBucketName, cosFeedbackRegion);
        return prefixes;
    }

    private void addResourcePrefixes(List<SourcePrefix> prefixes,
                                     String resourceType,
                                     String domain,
                                     String endpoint,
                                     String bucketName) {
        String domainPrefix = asPrefix(domain);
        if (domainPrefix != null) {
            prefixes.add(new SourcePrefix(resourceType, domainPrefix));
        }
        String endpointPrefix = asEndpointBucketPrefix(endpoint, bucketName);
        if (endpointPrefix != null) {
            prefixes.add(new SourcePrefix(resourceType, endpointPrefix));
        }
    }

    private void addCosPrefixes(List<SourcePrefix> prefixes,
                                String resourceType,
                                String domain,
                                String bucketName,
                                String region) {
        String domainPrefix = asPrefix(domain);
        if (domainPrefix != null) {
            prefixes.add(new SourcePrefix(resourceType, domainPrefix));
        }
        String safeBucket = safeText(bucketName);
        String safeRegion = safeText(region);
        if (!safeBucket.isBlank() && !safeRegion.isBlank()) {
            prefixes.add(new SourcePrefix(resourceType, "https://" + safeBucket + ".cos." + safeRegion + ".myqcloud.com/"));
        }
    }

    private String buildPublicUrl(AssetNode node, String resourceType, String objectKey) {
        String safeKey = safeObjectKey(objectKey);
        if (safeKey == null || safeKey.isBlank()) {
            return null;
        }
        if (node == AssetNode.COS) {
            if (RESOURCE_WALLPAPER.equals(resourceType)) {
                return buildByCos(cosWallpaperDomain, cosWallpaperBucketName, cosWallpaperRegion, safeKey);
            }
            if (RESOURCE_FEEDBACK.equals(resourceType)) {
                return buildByCos(cosFeedbackDomain, cosFeedbackBucketName, cosFeedbackRegion, safeKey);
            }
            return buildByCos(cosAvatarDomain, cosAvatarBucketName, cosAvatarRegion, safeKey);
        }
        if (node == AssetNode.OSS) {
            if (RESOURCE_WALLPAPER.equals(resourceType)) {
                return buildByDomainOrEndpoint(ossWallpaperDomain, ossWallpaperEndpoint, ossWallpaperBucketName, safeKey);
            }
            if (RESOURCE_FEEDBACK.equals(resourceType)) {
                return buildByDomainOrEndpoint(ossFeedbackDomain, ossFeedbackEndpoint, ossFeedbackBucketName, safeKey);
            }
            return buildByDomainOrEndpoint(ossAvatarDomain, ossAvatarEndpoint, ossAvatarBucketName, safeKey);
        }
        if (RESOURCE_WALLPAPER.equals(resourceType)) {
            return buildByDomainOrEndpoint(r2WallpaperDomain, r2WallpaperEndpoint, r2WallpaperBucketName, safeKey);
        }
        if (RESOURCE_FEEDBACK.equals(resourceType)) {
            return buildByDomainOrEndpoint(r2FeedbackDomain, r2FeedbackEndpoint, r2FeedbackBucketName, safeKey);
        }
        return buildByDomainOrEndpoint(r2AvatarDomain, r2AvatarEndpoint, r2AvatarBucketName, safeKey);
    }

    private String buildByCos(String domain, String bucketName, String region, String objectKey) {
        String domainPrefix = asPrefix(domain);
        if (domainPrefix != null) {
            return domainPrefix + objectKey;
        }
        String safeBucket = safeText(bucketName);
        String safeRegion = safeText(region);
        if (safeBucket.isBlank() || safeRegion.isBlank()) {
            return null;
        }
        return "https://" + safeBucket + ".cos." + safeRegion + ".myqcloud.com/" + objectKey;
    }

    private String buildByDomainOrEndpoint(String domain, String endpoint, String bucketName, String objectKey) {
        String domainPrefix = asPrefix(domain);
        if (domainPrefix != null) {
            return domainPrefix + objectKey;
        }
        String endpointPrefix = asEndpointBucketPrefix(endpoint, bucketName);
        if (endpointPrefix == null) {
            return null;
        }
        return endpointPrefix + objectKey;
    }

    private String normalizeResourceForNode(String resourceType, AssetNode node) {
        if (RESOURCE_ADMIN_AVATAR.equals(resourceType) && (node == AssetNode.COS || node == AssetNode.R2)) {
            return RESOURCE_AVATAR;
        }
        return resourceType;
    }

    private String inferObjectKeyByFolder(String url) {
        String lower = safeText(url).toLowerCase(Locale.ROOT);
        int idx = lower.indexOf("/wallpapers/");
        if (idx >= 0) {
            return safeText(url).substring(idx + 1);
        }
        idx = lower.indexOf("/user-avatars/");
        if (idx >= 0) {
            return safeText(url).substring(idx + 1);
        }
        idx = lower.indexOf("/admin-avatars/");
        if (idx >= 0) {
            return safeText(url).substring(idx + 1);
        }
        idx = lower.indexOf("/feedback-logs/");
        if (idx >= 0) {
            return safeText(url).substring(idx + 1);
        }
        idx = lower.indexOf("/feedback-screenshots/");
        if (idx >= 0) {
            return safeText(url).substring(idx + 1);
        }
        return null;
    }

    private String resolveResourceTypeByKey(String objectKey) {
        String lower = safeText(objectKey).toLowerCase(Locale.ROOT);
        if (lower.startsWith("wallpapers/")) {
            return RESOURCE_WALLPAPER;
        }
        if (lower.startsWith("feedback") || lower.startsWith("issue-feedback/")) {
            return RESOURCE_FEEDBACK;
        }
        if (lower.startsWith("admin-avatars/") || lower.startsWith("admin-avatar/")) {
            return RESOURCE_ADMIN_AVATAR;
        }
        return RESOURCE_AVATAR;
    }

    private String stripPrefix(String url, String prefix) {
        if (url == null || prefix == null) {
            return null;
        }
        String safeUrl = safeText(url);
        if (!safeUrl.startsWith(prefix)) {
            return null;
        }
        String key = safeUrl.substring(prefix.length());
        return safeObjectKey(key);
    }

    private String asPrefix(String rawBase) {
        String base = safeText(rawBase);
        if (base.isBlank()) {
            return null;
        }
        String withScheme = normalizeWithScheme(base);
        return withScheme.endsWith("/") ? withScheme : withScheme + "/";
    }

    private String asEndpointBucketPrefix(String endpoint, String bucketName) {
        String safeEndpoint = safeText(endpoint);
        String safeBucket = safeText(bucketName);
        if (safeEndpoint.isBlank() || safeBucket.isBlank()) {
            return null;
        }
        String normalized = normalizeWithScheme(safeEndpoint);
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized + "/" + safeBucket + "/";
    }

    private String normalizeWithScheme(String raw) {
        String safe = safeText(raw);
        if (safe.startsWith("http://") || safe.startsWith("https://")) {
            return safe;
        }
        return "https://" + safe;
    }

    private String safeObjectKey(String objectKey) {
        String safe = safeText(objectKey);
        while (safe.startsWith("/")) {
            safe = safe.substring(1);
        }
        return safe;
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    public enum AssetNode {
        R2,
        COS,
        OSS;

        public static AssetNode fromRaw(String raw) {
            if (raw == null) {
                return R2;
            }
            String normalized = raw.trim().toLowerCase(Locale.ROOT);
            if ("cos".equals(normalized) || "tencent-cos".equals(normalized)) {
                return COS;
            }
            if ("oss".equals(normalized) || "aliyun-oss".equals(normalized)) {
                return OSS;
            }
            return R2;
        }
    }

    private record SourcePrefix(String resourceType, String prefix) {
    }

    private record ParsedAsset(String resourceType, String objectKey) {
    }
}
