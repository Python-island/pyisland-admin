package com.pyisland.server.entity;

import java.time.LocalDateTime;

public class AppVersion {

    private Long id;
    private String appName;
    private String version;
    private String description;
    private LocalDateTime updatedAt;

    public AppVersion() {
    }

    public AppVersion(String appName, String version, String description) {
        this.appName = appName;
        this.version = version;
        this.description = description;
        this.updatedAt = java.time.LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public java.time.LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(java.time.LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
