package com.pyisland.server.service;

import com.pyisland.server.entity.AppVersion;
import com.pyisland.server.repository.AppVersionMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AppVersionService {

    private final AppVersionMapper appVersionMapper;

    public AppVersionService(AppVersionMapper appVersionMapper) {
        this.appVersionMapper = appVersionMapper;
    }

    public AppVersion getVersion(String appName) {
        return appVersionMapper.selectByAppName(appName);
    }

    public java.util.List<AppVersion> listAll() {
        return appVersionMapper.selectAll();
    }

    public boolean deleteVersion(String appName) {
        return appVersionMapper.deleteByAppName(appName) > 0;
    }

    public AppVersion createVersion(String appName, String version, String description) {
        AppVersion existing = appVersionMapper.selectByAppName(appName);
        if (existing != null) {
            return null;
        }
        AppVersion appVersion = new AppVersion(appName, version, description);
        appVersionMapper.insert(appVersion);
        return appVersion;
    }

    public AppVersion updateVersion(String appName, String version, String description) {
        AppVersion existing = appVersionMapper.selectByAppName(appName);
        if (existing != null) {
            existing.setVersion(version);
            existing.setDescription(description);
            existing.setUpdatedAt(LocalDateTime.now());
            appVersionMapper.updateByAppName(existing);
            return existing;
        } else {
            AppVersion appVersion = new AppVersion(appName, version, description);
            appVersionMapper.insert(appVersion);
            return appVersion;
        }
    }
}
