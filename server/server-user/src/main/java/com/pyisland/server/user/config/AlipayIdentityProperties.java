package com.pyisland.server.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 支付宝身份认证配置。
 * 复用支付宝支付应用的 appId / 私钥 / 公钥，新增身份认证专用 returnUrl。
 */
@Component
@ConfigurationProperties(prefix = "identity.alipay")
public class AlipayIdentityProperties {

    private boolean enabled;
    private String gatewayUrl = "https://openapi.alipay.com/gateway.do";
    private String appId;
    private String privateKeyPath;
    private String publicKeyPath;
    private String signType = "RSA2";
    private String charset = "UTF-8";
    private String returnUrl;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getGatewayUrl() {
        return gatewayUrl;
    }

    public void setGatewayUrl(String gatewayUrl) {
        this.gatewayUrl = gatewayUrl;
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

    public String getReturnUrl() {
        return returnUrl;
    }

    public void setReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
    }

    public boolean isConfigured() {
        return enabled
                && notBlank(appId)
                && notBlank(privateKeyPath)
                && notBlank(publicKeyPath);
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
