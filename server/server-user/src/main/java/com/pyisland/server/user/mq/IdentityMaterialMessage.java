package com.pyisland.server.user.mq;

/**
 * 身份认证人脸素材异步上传消息。
 *
 * @param username     用户名。
 * @param certifyId    支付宝认证流水号。
 * @param materialInfo 人脸素材 JSON 原文。
 * @param lastError    上次错误信息（重试时携带）。
 */
public record IdentityMaterialMessage(
        String username,
        String certifyId,
        String materialInfo,
        String lastError
) {
    public IdentityMaterialMessage(String username, String certifyId, String materialInfo) {
        this(username, certifyId, materialInfo, null);
    }
}
