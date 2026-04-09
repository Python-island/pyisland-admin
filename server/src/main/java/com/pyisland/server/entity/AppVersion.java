package com.pyisland.server.entity;

import java.time.LocalDateTime;

/**
 * 应用版本实体。
 */
public class AppVersion {

    private Long id;
    private String appName;
    private String version;
    private String description;
    private String downloadUrl;
    private LocalDateTime updatedAt;

    /**
     * 默认构造函数。
     */
    public AppVersion() {
    }

    /**
     * 使用基础字段构造版本实体。
     * @param appName 应用名称。
     * @param version 版本号。
     * @param description 版本描述。
     * @param downloadUrl 下载链接。
     */
    public AppVersion(String appName, String version, String description, String downloadUrl) {
        this.appName = appName;
        this.version = version;
        this.description = description;
        this.downloadUrl = downloadUrl;
        this.updatedAt = java.time.LocalDateTime.now();
    }

    /**
     * 获取主键 ID。
     * @return 版本 ID。
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置主键 ID。
     * @param id 版本 ID。
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取应用名称。
     * @return 应用名称。
     */
    public String getAppName() {
        return appName;
    }

    /**
     * 设置应用名称。
     * @param appName 应用名称。
     */
    public void setAppName(String appName) {
        this.appName = appName;
    }

    /**
     * 获取版本号。
     * @return 版本号。
     */
    public String getVersion() {
        return version;
    }

    /**
     * 设置版本号。
     * @param version 版本号。
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * 获取版本描述。
     * @return 版本描述。
     */
    public String getDescription() {
        return description;
    }

    /**
     * 设置版本描述。
     * @param description 版本描述。
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * 获取下载链接。
     * @return 下载链接。
     */
    public String getDownloadUrl() {
        return downloadUrl;
    }

    /**
     * 设置下载链接。
     * @param downloadUrl 下载链接。
     */
    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    /**
     * 获取更新时间。
     * @return 更新时间。
     */
    public java.time.LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * 设置更新时间。
     * @param updatedAt 更新时间。
     */
    public void setUpdatedAt(java.time.LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
