package com.pyisland.server.auth.mq;

import com.pyisland.server.auth.config.EmailVerificationMqConfig;
import com.pyisland.server.auth.service.EmailVerificationService;
import com.pyisland.server.auth.service.ResendEmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 邮箱验证码投递消费者（占位）。
 * 下一步将接入 Resend 实际发送。
 */
@Component
public class EmailCodeDispatchConsumer {

    private static final Logger log = LoggerFactory.getLogger(EmailCodeDispatchConsumer.class);
    private final ResendEmailService resendEmailService;

    public EmailCodeDispatchConsumer(ResendEmailService resendEmailService) {
        this.resendEmailService = resendEmailService;
    }

    @RabbitListener(queues = EmailVerificationMqConfig.EMAIL_CODE_QUEUE)
    public void onMessage(EmailCodeDispatchMessage message) {
        if (message == null) {
            return;
        }
        EmailVerificationService.Scene scene;
        try {
            scene = EmailVerificationService.Scene.valueOf(message.scene());
        } catch (Exception ex) {
            log.warn("email code dispatch ignored due to invalid scene traceId={} scene={}", message.traceId(), message.scene());
            return;
        }

        try {
            resendEmailService.sendVerificationCode(message.email(), scene, message.code(), message.traceId());
            log.info("email code dispatched traceId={} email={} scene={}",
                    message.traceId(),
                    message.email(),
                    message.scene());
        } catch (Exception ex) {
            log.error("email code dispatch failed traceId={} email={} scene={} err={}",
                    message.traceId(),
                    message.email(),
                    message.scene(),
                    ex.getMessage());
            throw new IllegalStateException("email code dispatch failed", ex);
        }
    }
}
