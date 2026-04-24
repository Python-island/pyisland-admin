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
    private String appId;
    private String privateKeyPath;
    private String publicKeyPath;
    private String notifyUrl;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAppId() {
        return appId;
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
