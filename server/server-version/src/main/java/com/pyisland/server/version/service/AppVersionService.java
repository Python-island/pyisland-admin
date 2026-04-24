package com.pyisland.server.version.service;

import com.pyisland.server.version.entity.AppVersion;
import com.pyisland.server.version.mapper.AppVersionMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 应用版本服务。
 */
@Service
public class AppVersionService {

    private final AppVersionMapper appVersionMapper;
    private final VersionAppBloomService versionAppBloomService;

    /**
     * 构造版本服务。
     * @param appVersionMapper 版本数据访问接口。
     */
    public AppVersionService(AppVersionMapper appVersionMapper,
                             VersionAppBloomService versionAppBloomService) {
        this.appVersionMapper = appVersionMapper;
        this.versionAppBloomService = versionAppBloomService;
        rebuildVersionAppBloom();
    }

    private void rebuildVersionAppBloom() {
        List<AppVersion> versions = appVersionMapper.selectAll();
        versionAppBloomService.rebuildFromAppNames(versions.stream()
                .map(AppVersion::getAppName)
                .toList());
    }

    private String normalizeAppName(String appName) {
        if (appName == null) {
            return null;
        }
        String normalized = appName.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * 查询指定应用版本。
     * @param appName 应用名称。
     * @return 版本信息。
     */
    @Cacheable(cacheNames = "app-version", key = "#appName")
    public AppVersion getVersion(String appName) {
        String normalizedAppName = normalizeAppName(appName);
        if (normalizedAppName == null) {
            return null;
        }
        if (!versionAppBloomService.mightContain(normalizedAppName)) {
            return null;
        }
        return appVersionMapper.selectByAppName(normalizedAppName);
    }

    /**
     * 查询全部应用版本。
     * @return 版本列表。
     */
    @Cacheable(cacheNames = "app-version-list", key = "'all'")
    public java.util.List<AppVersion> listAll() {
        return appVersionMapper.selectAll();
    }

    /**
     * 删除指定应用版本。
     * @param appName 应用名称。
     * @return 是否删除成功。
     */
    @Caching(evict = {
            @CacheEvict(cacheNames = "app-version", key = "#appName", condition = "#result"),
            @CacheEvict(cacheNames = "app-version-list", key = "'all'", condition = "#result")
    })
    public boolean deleteVersion(String appName) {
        String normalizedAppName = normalizeAppName(appName);
        if (normalizedAppName == null) {
            return false;
        }
        boolean deleted = appVersionMapper.deleteByAppName(normalizedAppName) > 0;
        if (deleted) {
            versionAppBloomService.remove(normalizedAppName);
        }
        return deleted;
    }

    /**
     * 创建应用版本。
     * @param appName 应用名称。
     * @param version 版本号。
     * @param description 版本描述。
     * @param downloadUrl 下载地址。
     * @return 创建后的版本；若应用已存在返回 null。
     */
    @Caching(evict = {
            @CacheEvict(cacheNames = "app-version", key = "#appName", condition = "#result != null"),
            @CacheEvict(cacheNames = "app-version-list", key = "'all'", condition = "#result != null")
    })
    public AppVersion createVersion(String appName, String version, String description, String downloadUrl) {
        String normalizedAppName = normalizeAppName(appName);
        if (normalizedAppName == null) {
            return null;
        }
        AppVersion existing = appVersionMapper.selectByAppName(normalizedAppName);
        if (existing != null) {
            return null;
        }
        AppVersion appVersion = new AppVersion(normalizedAppName, version, description, downloadUrl);
        appVersionMapper.insert(appVersion);
        versionAppBloomService.add(normalizedAppName);
        return appVersion;
    }

    /**
     * 更新应用版本。
     * @param appName 应用名称。
     * @param version 版本号。
     * @param description 版本描述。
     * @param downloadUrl 下载地址。
     * @return 更新后的版本信息。
     */
    @Caching(evict = {
            @CacheEvict(cacheNames = "app-version", key = "#appName"),
            @CacheEvict(cacheNames = "app-version-list", key = "'all'")
    })
    public AppVersion updateVersion(String appName, String version, String description, String downloadUrl) {
        String normalizedAppName = normalizeAppName(appName);
        if (normalizedAppName == null) {
            return null;
        }
        AppVersion existing = appVersionMapper.selectByAppName(normalizedAppName);
        if (existing != null) {
            existing.setVersion(version);
            existing.setDescription(description);
            existing.setDownloadUrl(downloadUrl);
            existing.setUpdatedAt(LocalDateTime.now());
            appVersionMapper.updateByAppName(existing);
            versionAppBloomService.add(normalizedAppName);
            return existing;
        } else {
            AppVersion appVersion = new AppVersion(normalizedAppName, version, description, downloadUrl);
            appVersionMapper.insert(appVersion);
            versionAppBloomService.add(normalizedAppName);
            return appVersion;
        }
    }

    /**
     * 递增指定应用和版本的更新统计次数。
     * @param appName 应用名称。
     * @param version 版本号。
     * @return 是否更新成功。
     */
    @Caching(evict = {
            @CacheEvict(cacheNames = "app-version", key = "#appName", condition = "#result"),
            @CacheEvict(cacheNames = "app-version-list", key = "'all'", condition = "#result")
    })
    public boolean incrementUpdateCount(String appName, String version) {
        String normalizedAppName = normalizeAppName(appName);
        if (normalizedAppName == null) {
            return false;
        }
        return appVersionMapper.incrementUpdateCountByAppNameAndVersion(normalizedAppName, version) > 0;
    }
}
