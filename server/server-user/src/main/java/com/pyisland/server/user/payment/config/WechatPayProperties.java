package com.pyisland.server.user.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信支付配置。
 */
@Component
@ConfigurationProperties(prefix = "payment.wechat")
public class WechatPayProperties {

    private boolean enabled;
    private String mchId;
    private String appId;
    private String apiV3Key;
    private String privateKeyPath;
    private String serialNo;
    private String notifyUrl;
    private String platformCertPath;
    private int orderExpireMinutes = 15;
    private int queryPendingBatchSize = 100;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getMchId() {
        return mchId;
    }

    public void setMchId(String mchId) {
        this.mchId = mchId;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getApiV3Key() {
        return apiV3Key;
    }

    public void setApiV3Key(String apiV3Key) {
        this.apiV3Key = apiV3Key;
    }

    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    public void setPrivateKeyPath(String privateKeyPath) {
        this.privateKeyPath = privateKeyPath;
    }

    public String getSerialNo() {
        return serialNo;
    }

    public void setSerialNo(String serialNo) {
        this.serialNo = serialNo;
    }

    public String getNotifyUrl() {
        return notifyUrl;
    }

    public void setNotifyUrl(String notifyUrl) {
        this.notifyUrl = notifyUrl;
    }

    public String getPlatformCertPath() {
        return platformCertPath;
    }

    public void setPlatformCertPath(String platformCertPath) {
        this.platformCertPath = platformCertPath;
    }

    public int getOrderExpireMinutes() {
        return orderExpireMinutes;
    }

    public void setOrderExpireMinutes(int orderExpireMinutes) {
        this.orderExpireMinutes = orderExpireMinutes;
    }

    public int getQueryPendingBatchSize() {
        return queryPendingBatchSize;
    }

    public void setQueryPendingBatchSize(int queryPendingBatchSize) {
        this.queryPendingBatchSize = queryPendingBatchSize;
    }

    public boolean isConfigured() {
        return enabled
                && notBlank(mchId)
                && notBlank(appId)
                && notBlank(apiV3Key)
                && notBlank(privateKeyPath)
                && notBlank(serialNo)
                && notBlank(notifyUrl);
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
