package com.pyisland.server.auth.mq;

/**
 * 邮箱验证码投递消息。
 * @param traceId 追踪标识。
 * @param email 目标邮箱。
 * @param scene 验证码场景。
 * @param code 明文验证码。
 * @param createdAtEpochSeconds 秒级时间戳。
 */
public record EmailCodeDispatchMessage(
        String traceId,
        String email,
        String scene,
        String code,
        long createdAtEpochSeconds
) {
}
