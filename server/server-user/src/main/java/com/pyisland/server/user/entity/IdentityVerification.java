package com.pyisland.server.user.entity;

import java.time.LocalDateTime;

/**
 * 身份认证记录实体。
 */
public class IdentityVerification {

    public static final String STATUS_INIT = "INIT";
    public static final String STATUS_CERTIFYING = "CERTIFYING";
    public static final String STATUS_PASSED = "PASSED";
    public static final String STATUS_FAILED = "FAILED";

    private Long id;
    private String username;
    private String outerOrderNo;
    private String certifyId;
    private String certNameCiphertext;
    private String certNoCiphertext;
    private String status;
    private String materialInfoUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getOuterOrderNo() {
        return outerOrderNo;
    }

    public void setOuterOrderNo(String outerOrderNo) {
        this.outerOrderNo = outerOrderNo;
    }

    public String getCertifyId() {
        return certifyId;
    }

    public void setCertifyId(String certifyId) {
        this.certifyId = certifyId;
    }

    public String getCertNameCiphertext() {
        return certNameCiphertext;
    }

    public void setCertNameCiphertext(String certNameCiphertext) {
        this.certNameCiphertext = certNameCiphertext;
    }

    public String getCertNoCiphertext() {
        return certNoCiphertext;
    }

    public void setCertNoCiphertext(String certNoCiphertext) {
        this.certNoCiphertext = certNoCiphertext;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMaterialInfoUrl() {
        return materialInfoUrl;
    }

    public void setMaterialInfoUrl(String materialInfoUrl) {
        this.materialInfoUrl = materialInfoUrl;
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
}
