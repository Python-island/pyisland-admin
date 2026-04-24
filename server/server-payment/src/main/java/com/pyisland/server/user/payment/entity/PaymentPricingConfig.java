package com.pyisland.server.user.payment.entity;

import java.time.LocalDateTime;

/**
 * 支付定价与权益配置实体。
 */
public class PaymentPricingConfig {

    private Long id;
    private Integer proMonthAmountFen;
    private String freeDesc;
    private String freeFeaturesText;
    private String proDesc;
    private String proFeaturesText;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getProMonthAmountFen() {
        return proMonthAmountFen;
    }

    public void setProMonthAmountFen(Integer proMonthAmountFen) {
        this.proMonthAmountFen = proMonthAmountFen;
    }

    public String getFreeDesc() {
        return freeDesc;
    }

    public void setFreeDesc(String freeDesc) {
        this.freeDesc = freeDesc;
    }

    public String getFreeFeaturesText() {
        return freeFeaturesText;
    }

    public void setFreeFeaturesText(String freeFeaturesText) {
        this.freeFeaturesText = freeFeaturesText;
    }

    public String getProDesc() {
        return proDesc;
    }

    public void setProDesc(String proDesc) {
        this.proDesc = proDesc;
    }

    public String getProFeaturesText() {
        return proFeaturesText;
    }

    public void setProFeaturesText(String proFeaturesText) {
        this.proFeaturesText = proFeaturesText;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
