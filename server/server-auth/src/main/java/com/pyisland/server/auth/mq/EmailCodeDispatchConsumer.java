package com.pyisland.server.auth.mq;

import com.pyisland.server.auth.config.EmailVerificationMqConfig;
import com.pyisland.server.auth.service.EmailVerificationService;
import com.pyisland.server.auth.service.ResendEmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * 邮箱验证码投递消费者（占位）。
 * 下一步将接入 Resend 实际发送。
 */
@Component
public class EmailCodeDispatchConsumer {

    private static final Logger log = LoggerFactory.getLogger(EmailCodeDispatchConsumer.class);
    private final ResendEmailService resendEmailService;
    private final EmailVerificationService emailVerificationService;
    private final RabbitTemplate rabbitTemplate;
    private final int emailNotifyMaxRetries;

    public EmailCodeDispatchConsumer(ResendEmailService resendEmailService,
                                     EmailVerificationService emailVerificationService,
                                     RabbitTemplate rabbitTemplate,
                                     @Value("${email.notify-max-retries:3}") int emailNotifyMaxRetries) {
        this.resendEmailService = resendEmailService;
        this.emailVerificationService = emailVerificationService;
        this.rabbitTemplate = rabbitTemplate;
        this.emailNotifyMaxRetries = Math.max(0, emailNotifyMaxRetries);
    }

    @RabbitListener(queues = EmailVerificationMqConfig.EMAIL_CODE_QUEUE)
    public void onMessage(EmailCodeDispatchMessage message,
                          @Header(name = EmailVerificationMqConfig.EMAIL_RETRY_HEADER, required = false) Integer retryCount) {
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
            routeToRetryOrDlq(message, retryCount, ex);
        }
    }

    @RabbitListener(queues = EmailVerificationMqConfig.EMAIL_CODE_DLQ)
    public void onDeadLetter(EmailCodeDispatchMessage message,
                             @Header(name = EmailVerificationMqConfig.EMAIL_RETRY_HEADER, required = false) Integer retryCount) {
        if (message == null) {
            return;
        }
        int deadRetry = retryCount == null ? 0 : Math.max(0, retryCount);
        emailVerificationService.logDispatchDlq(
                message.traceId(),
                message.email(),
                message.scene(),
                deadRetry,
                message.lastError()
        );
        log.error("email code entered dlq traceId={} email={} scene={} retryCount={}",
                message.traceId(), message.email(), message.scene(), deadRetry);
    }

    private void routeToRetryOrDlq(EmailCodeDispatchMessage message, Integer retryCount, Exception ex) {
        int currentRetry = retryCount == null ? 0 : Math.max(0, retryCount);
        int nextRetry = currentRetry + 1;
        String errorMessage = ex == null ? "unknown" : ex.getMessage();
        EmailCodeDispatchMessage failedMessage = new EmailCodeDispatchMessage(
                message.traceId(),
                message.email(),
                message.scene(),
                message.code(),
                message.createdAtEpochSeconds(),
                errorMessage
        );
        MessagePostProcessor setRetryHeader = m -> {
            m.getMessageProperties().setHeader(EmailVerificationMqConfig.EMAIL_RETRY_HEADER, nextRetry);
            return m;
        };

        if (currentRetry < emailNotifyMaxRetries) {
            rabbitTemplate.convertAndSend(
                    EmailVerificationMqConfig.EMAIL_CODE_EXCHANGE,
                    EmailVerificationMqConfig.EMAIL_CODE_RETRY_ROUTING_KEY,
                    failedMessage,
                    setRetryHeader
            );
            log.warn("email code routed to retry traceId={} email={} retry={}/{} err={}",
                    message.traceId(), message.email(), nextRetry, emailNotifyMaxRetries, errorMessage);
            return;
        }

        rabbitTemplate.convertAndSend(
                EmailVerificationMqConfig.EMAIL_CODE_EXCHANGE,
                EmailVerificationMqConfig.EMAIL_CODE_DLQ_ROUTING_KEY,
                failedMessage,
                setRetryHeader
        );
        log.error("email code routed to dlq traceId={} email={} retry={} err={}",
                message.traceId(), message.email(), nextRetry, errorMessage);
    }
}
