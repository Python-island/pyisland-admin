package com.pyisland.server.user.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 壁纸市场资源实体。
 */
public class WallpaperAsset {

    private Long id;
    private String ownerUsername;
    private String title;
    private String description;
    private String type;
    private String status;
    private String originalUrl;
    private String thumb320Url;
    private String thumb720Url;
    private String thumb1280Url;
    private Integer width;
    private Integer height;
    private Long durationMs;
    private BigDecimal frameRate;
    private Long fileSize;
    private String tagsText;
    private Boolean copyrightDeclared;
    private BigDecimal ratingAvg;
    private Long ratingCount;
    private Long downloadCount;
    private Long applyCount;
    private Integer currentVersion;
    private Boolean deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime publishedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public void setOwnerUsername(String ownerUsername) {
        this.ownerUsername = ownerUsername;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }

    public String getThumb320Url() {
        return thumb320Url;
    }

    public void setThumb320Url(String thumb320Url) {
        this.thumb320Url = thumb320Url;
    }

    public String getThumb720Url() {
        return thumb720Url;
    }

    public void setThumb720Url(String thumb720Url) {
        this.thumb720Url = thumb720Url;
    }

    public String getThumb1280Url() {
        return thumb1280Url;
    }

    public void setThumb1280Url(String thumb1280Url) {
        this.thumb1280Url = thumb1280Url;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public BigDecimal getFrameRate() {
        return frameRate;
    }

    public void setFrameRate(BigDecimal frameRate) {
        this.frameRate = frameRate;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getTagsText() {
        return tagsText;
    }

    public void setTagsText(String tagsText) {
        this.tagsText = tagsText;
    }

    public Boolean getCopyrightDeclared() {
        return copyrightDeclared;
    }

    public void setCopyrightDeclared(Boolean copyrightDeclared) {
        this.copyrightDeclared = copyrightDeclared;
    }

    public BigDecimal getRatingAvg() {
        return ratingAvg;
    }

    public void setRatingAvg(BigDecimal ratingAvg) {
        this.ratingAvg = ratingAvg;
    }

    public Long getRatingCount() {
        return ratingCount;
    }

    public void setRatingCount(Long ratingCount) {
        this.ratingCount = ratingCount;
    }

    public Long getDownloadCount() {
        return downloadCount;
    }

    public void setDownloadCount(Long downloadCount) {
        this.downloadCount = downloadCount;
    }

    public Long getApplyCount() {
        return applyCount;
    }

    public void setApplyCount(Long applyCount) {
        this.applyCount = applyCount;
    }

    public Integer getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(Integer currentVersion) {
        this.currentVersion = currentVersion;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }
}
