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
