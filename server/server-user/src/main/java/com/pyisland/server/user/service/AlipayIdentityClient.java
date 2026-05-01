package com.pyisland.server.user.service;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayUserCertifyOpenCertifyModel;
import com.alipay.api.domain.AlipayUserCertifyOpenInitializeModel;
import com.alipay.api.domain.AlipayUserCertifyOpenQueryModel;
import com.alipay.api.domain.OpenCertifyIdentityParam;
import com.alipay.api.domain.OpenCertifyMerchantConfig;
import com.alipay.api.request.AlipayUserCertifyOpenCertifyRequest;
import com.alipay.api.request.AlipayUserCertifyOpenInitializeRequest;
import com.alipay.api.request.AlipayUserCertifyOpenQueryRequest;
import com.alipay.api.response.AlipayUserCertifyOpenCertifyResponse;
import com.alipay.api.response.AlipayUserCertifyOpenInitializeResponse;
import com.alipay.api.response.AlipayUserCertifyOpenQueryResponse;
import com.pyisland.server.user.config.AlipayIdentityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 支付宝身份认证 SDK 客户端封装。
 * 包含三个核心接口：初始化 / 开始认证（获取 URL）/ 查询结果。
 */
@Component
public class AlipayIdentityClient {

    private static final Logger log = LoggerFactory.getLogger(AlipayIdentityClient.class);

    private final AlipayIdentityProperties properties;

    public AlipayIdentityClient(AlipayIdentityProperties properties) {
        this.properties = properties;
    }

    public boolean isAvailable() {
        return properties.isConfigured();
    }

    /**
     * 身份认证初始化：调用 alipay.user.certify.open.initialize，获取 certify_id。
     *
     * @param outerOrderNo 商户唯一订单号。
     * @param certName     真实姓名。
     * @param certNo       身份证号。
     * @return 初始化结果，包含 certifyId。
     */
    public InitializeResult initialize(String outerOrderNo, String certName, String certNo) throws Exception {
        if (!isAvailable()) {
            throw new IllegalStateException("支付宝身份认证未启用或配置不完整");
        }
        AlipayClient client = buildClient();

        AlipayUserCertifyOpenInitializeRequest request = new AlipayUserCertifyOpenInitializeRequest();
        AlipayUserCertifyOpenInitializeModel model = new AlipayUserCertifyOpenInitializeModel();
        model.setOuterOrderNo(outerOrderNo);
        model.setBizCode("FACE");

        OpenCertifyIdentityParam identityParam = new OpenCertifyIdentityParam();
        identityParam.setIdentityType("CERT_INFO");
        identityParam.setCertType("IDENTITY_CARD");
        identityParam.setCertName(certName);
        identityParam.setCertNo(certNo);
        model.setIdentityParam(identityParam);

        OpenCertifyMerchantConfig merchantConfig = new OpenCertifyMerchantConfig();
        String returnUrl = properties.getReturnUrl();
        if (returnUrl != null && !returnUrl.isBlank()) {
            merchantConfig.setReturnUrl(returnUrl);
        }
        model.setMerchantConfig(merchantConfig);

        request.setBizModel(model);

        AlipayUserCertifyOpenInitializeResponse response = client.execute(request);
        if (!response.isSuccess()) {
            log.warn("alipay identity initialize failed outerOrderNo={} subCode={} subMsg={}",
                    outerOrderNo, response.getSubCode(), response.getSubMsg());
            throw new IllegalStateException("身份认证初始化失败: " + response.getSubCode() + " " + response.getSubMsg());
        }
        return new InitializeResult(response.getCertifyId());
    }

    /**
     * 开始认证：调用 alipay.user.certify.open.certify，获取认证页面 URL。
     *
     * @param certifyId 初始化阶段获取的 certify_id。
     * @return 认证页面 URL。
     */
    public CertifyResult certify(String certifyId) throws Exception {
        if (!isAvailable()) {
            throw new IllegalStateException("支付宝身份认证未启用或配置不完整");
        }
        AlipayClient client = buildClient();

        AlipayUserCertifyOpenCertifyRequest request = new AlipayUserCertifyOpenCertifyRequest();
        AlipayUserCertifyOpenCertifyModel model = new AlipayUserCertifyOpenCertifyModel();
        model.setCertifyId(certifyId);
        request.setBizModel(model);

        AlipayUserCertifyOpenCertifyResponse response = client.pageExecute(request, "GET");
        if (!response.isSuccess()) {
            log.warn("alipay identity certify failed certifyId={} subCode={} subMsg={}",
                    certifyId, response.getSubCode(), response.getSubMsg());
            throw new IllegalStateException("身份认证获取URL失败: " + response.getSubCode() + " " + response.getSubMsg());
        }
        String certifyUrl = response.getBody();
        if (certifyUrl == null || certifyUrl.isBlank()) {
            throw new IllegalStateException("身份认证未返回认证链接");
        }
        return new CertifyResult(certifyUrl);
    }

    /**
     * 查询认证结果：调用 alipay.user.certify.open.query。
     *
     * @param certifyId certify_id。
     * @return 查询结果。
     */
    public QueryResult query(String certifyId) throws Exception {
        if (!isAvailable()) {
            throw new IllegalStateException("支付宝身份认证未启用或配置不完整");
        }
        AlipayClient client = buildClient();

        AlipayUserCertifyOpenQueryRequest request = new AlipayUserCertifyOpenQueryRequest();
        AlipayUserCertifyOpenQueryModel model = new AlipayUserCertifyOpenQueryModel();
        model.setCertifyId(certifyId);
        request.setBizModel(model);

        AlipayUserCertifyOpenQueryResponse response = client.execute(request);
        if (!response.isSuccess()) {
            log.warn("alipay identity query failed certifyId={} subCode={} subMsg={}",
                    certifyId, response.getSubCode(), response.getSubMsg());
            return new QueryResult(false, response.getSubCode(), response.getSubMsg(), null);
        }
        boolean passed = "T".equalsIgnoreCase(response.getPassed());
        return new QueryResult(passed, null, null, response.getMaterialInfo());
    }

    private AlipayClient buildClient() throws Exception {
        String privateKey = readKey(properties.getPrivateKeyPath());
        String publicKey = readKey(properties.getPublicKeyPath());
        return new DefaultAlipayClient(
                properties.getGatewayUrl(),
                properties.getAppId(),
                privateKey,
                "json",
                properties.getCharset(),
                publicKey,
                properties.getSignType()
        );
    }

    private String readKey(String path) throws Exception {
        if (path == null || path.isBlank()) {
            throw new IllegalStateException("支付宝密钥路径未配置");
        }
        return Files.readString(Path.of(path), StandardCharsets.UTF_8)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
    }

    public record InitializeResult(String certifyId) {
    }

    public record CertifyResult(String certifyUrl) {
    }

    public record QueryResult(boolean passed, String subCode, String subMsg, String materialInfo) {
    }
}
