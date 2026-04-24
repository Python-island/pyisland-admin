package com.pyisland.server.user.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 支付宝支付配置。
 */
@Component
@ConfigurationProperties(prefix = "payment.alipay")
public class AlipayProperties {

    private boolean enabled;
    private String gatewayUrl = "https://openapi.alipay.com/gateway.do";
    private String appId;
    private String privateKeyPath;
    private String publicKeyPath;
    private String notifyUrl;
    private String signType = "RSA2";
    private String charset = "UTF-8";
    private int queryPendingBatchSize = 100;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAppId() {
        return appId;
    }

    public String getGatewayUrl() {
        return gatewayUrl;
    }

    public void setGatewayUrl(String gatewayUrl) {
        this.gatewayUrl = gatewayUrl;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    public void setPrivateKeyPath(String privateKeyPath) {
        this.privateKeyPath = privateKeyPath;
    }

    public String getPublicKeyPath() {
        return publicKeyPath;
    }

    public void setPublicKeyPath(String publicKeyPath) {
        this.publicKeyPath = publicKeyPath;
    }

    public String getNotifyUrl() {
        return notifyUrl;
    }

    public void setNotifyUrl(String notifyUrl) {
        this.notifyUrl = notifyUrl;
    }

    public String getSignType() {
        return signType;
    }

    public void setSignType(String signType) {
        this.signType = signType;
    }

    public String getCharset() {
        return charset;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    public int getQueryPendingBatchSize() {
        return queryPendingBatchSize;
    }

    public void setQueryPendingBatchSize(int queryPendingBatchSize) {
        this.queryPendingBatchSize = queryPendingBatchSize;
    }

    public boolean isConfigured() {
        return enabled
                && notBlank(appId)
                && notBlank(privateKeyPath)
                && notBlank(publicKeyPath)
                && notBlank(notifyUrl);
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
