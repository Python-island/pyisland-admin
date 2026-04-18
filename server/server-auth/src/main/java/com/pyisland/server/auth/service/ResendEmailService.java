package com.pyisland.server.auth.service;

import com.resend.Resend;
import com.resend.services.emails.model.CreateEmailOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Resend 邮件发送服务。
 */
@Service
public class ResendEmailService {

    private final String resendApiKey;
    private final String resendFrom;

    public ResendEmailService(@Value("${resend.api-key:}") String resendApiKey,
                              @Value("${resend.from:}") String resendFrom) {
        this.resendApiKey = resendApiKey;
        this.resendFrom = resendFrom;
    }

    /**
     * 发送验证码邮件。
     */
    public void sendVerificationCode(String email,
                                     EmailVerificationService.Scene scene,
                                     String code,
                                     String traceId) {
        if (resendApiKey == null || resendApiKey.isBlank()) {
            throw new IllegalStateException("resend.api-key is missing");
        }
        if (resendFrom == null || resendFrom.isBlank()) {
            throw new IllegalStateException("resend.from is missing");
        }

        try {
            String subject = buildSubject(scene);
            String html = buildHtml(scene, code, traceId);

            Resend resend = new Resend(resendApiKey);
            CreateEmailOptions options = CreateEmailOptions.builder()
                    .from(resendFrom)
                    .to(List.of(email))
                    .subject(subject)
                    .html(html)
                    .build();
            resend.emails().send(options);
        } catch (Exception ex) {
            throw new IllegalStateException("resend send email failed", ex);
        }
    }

    private String buildSubject(EmailVerificationService.Scene scene) {
        return switch (scene) {
            case REGISTER -> "eIsland 注册验证码";
            case LOGIN -> "eIsland 登录验证码";
            case RESET_PASSWORD -> "eIsland 重置密码验证码";
            case CHANGE_EMAIL -> "eIsland 更换邮箱验证码";
        };
    }

    private String buildHtml(EmailVerificationService.Scene scene, String code, String traceId) {
        String sceneLabel = switch (scene) {
            case REGISTER -> "注册账号";
            case LOGIN -> "登录账号";
            case RESET_PASSWORD -> "重置密码";
            case CHANGE_EMAIL -> "更换邮箱";
        };
        return """
                <div style=\"font-family:Arial,sans-serif;line-height:1.6;color:#111\"> 
                  <h2 style=\"margin:0 0 12px 0\">eIsland 邮箱验证码</h2>
                  <p>你正在进行：<strong>%s</strong></p>
                  <p>本次验证码（5 分钟内有效）：</p>
                  <p style=\"font-size:24px;letter-spacing:4px;font-weight:700;margin:8px 0 12px\">%s</p>
                  <p style=\"color:#666\">如果不是你本人操作，请忽略本邮件。</p>
                  <p style=\"color:#999;font-size:12px\">traceId: %s</p>
                </div>
                """.formatted(sceneLabel, code, traceId == null ? "-" : traceId);
    }
}
