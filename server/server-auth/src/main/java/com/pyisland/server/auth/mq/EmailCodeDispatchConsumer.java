package com.pyisland.server.auth.mq;

import com.pyisland.server.auth.config.EmailVerificationMqConfig;
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

    @RabbitListener(queues = EmailVerificationMqConfig.EMAIL_CODE_QUEUE)
    public void onMessage(EmailCodeDispatchMessage message) {
        if (message == null) {
            return;
        }
        log.info("email code dispatch queued traceId={} email={} scene={}",
                message.traceId(),
                message.email(),
                message.scene());
    }
}
