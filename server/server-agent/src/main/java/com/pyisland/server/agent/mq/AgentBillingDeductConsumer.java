package com.pyisland.server.agent.mq;

import com.pyisland.server.agent.config.AgentBillingMqConfig;
import com.pyisland.server.user.entity.AgentBillingDlqLog;
import com.pyisland.server.user.mapper.AgentBillingDlqLogMapper;
import com.pyisland.server.user.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Agent 计费扣减异步落库消费者。
 * 从 RabbitMQ 消费扣减消息，写入 MySQL。失败时重试，最终进入 DLQ 并记录异常表。
 */
@Component
public class AgentBillingDeductConsumer {

    private static final Logger log = LoggerFactory.getLogger(AgentBillingDeductConsumer.class);
    private static final int MAX_RETRIES = 5;

    private final UserMapper userMapper;
    private final RabbitTemplate rabbitTemplate;
    private final AgentBillingDlqLogMapper dlqLogMapper;

    public AgentBillingDeductConsumer(UserMapper userMapper,
                                      RabbitTemplate rabbitTemplate,
                                      AgentBillingDlqLogMapper dlqLogMapper) {
        this.userMapper = userMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.dlqLogMapper = dlqLogMapper;
    }

    @RabbitListener(queues = AgentBillingMqConfig.QUEUE)
    public void onMessage(AgentBillingDeductMessage message,
                          @Header(name = AgentBillingMqConfig.RETRY_HEADER, required = false) Integer retryCount) {
        if (message == null || message.username() == null || message.amountFen() == null) {
            return;
        }
        try {
            BigDecimal amount = new BigDecimal(message.amountFen());
            int rows = userMapper.deductBalance(message.username(), amount);
            if (rows == 0) {
                log.warn("agent billing db-sync: DB deduct affected 0 rows (balance insufficient or user missing), username={}, amount={}",
                        message.username(), message.amountFen());
            } else {
                log.info("agent billing db-sync: synced deduction to DB, username={}, model={}, amount={}, inputTokens={}, outputTokens={}",
                        message.username(), message.modelName(), message.amountFen(), message.inputTokens(), message.outputTokens());
            }
        } catch (Exception ex) {
            log.error("agent billing db-sync: consume failed, username={}, amount={}, err={}",
                    message.username(), message.amountFen(), ex.getMessage());
            routeToRetryOrDlq(message, retryCount, ex);
        }
    }

    @RabbitListener(queues = AgentBillingMqConfig.DLQ)
    public void onDeadLetter(AgentBillingDeductMessage message,
                             @Header(name = AgentBillingMqConfig.RETRY_HEADER, required = false) Integer retryCount) {
        if (message == null) {
            return;
        }
        int deadRetry = retryCount == null ? 0 : Math.max(0, retryCount);
        log.error("agent billing db-sync: entered DLQ, username={}, model={}, amount={}, retryCount={}, lastError={}",
                message.username(), message.modelName(), message.amountFen(), deadRetry, message.lastError());

        try {
            AgentBillingDlqLog dlqLog = new AgentBillingDlqLog();
            dlqLog.setUsername(message.username());
            dlqLog.setAmountFen(message.amountFen());
            dlqLog.setModelName(message.modelName());
            dlqLog.setInputTokens(message.inputTokens());
            dlqLog.setOutputTokens(message.outputTokens());
            dlqLog.setRetryCount(deadRetry);
            dlqLog.setLastError(message.lastError());
            dlqLog.setStatus("pending");
            dlqLog.setCreatedAt(LocalDateTime.now());
            dlqLogMapper.insert(dlqLog);
            log.info("agent billing dlq: persisted DLQ record id={}, username={}", dlqLog.getId(), message.username());
        } catch (Exception ex) {
            log.error("agent billing dlq: failed to persist DLQ record, username={}, err={}",
                    message.username(), ex.getMessage(), ex);
        }
    }

    private void routeToRetryOrDlq(AgentBillingDeductMessage message, Integer retryCount, Exception ex) {
        int currentRetry = retryCount == null ? 0 : Math.max(0, retryCount);
        int nextRetry = currentRetry + 1;
        String errorMessage = ex == null ? "unknown" : ex.getMessage();
        AgentBillingDeductMessage failedMessage = new AgentBillingDeductMessage(
                message.username(), message.amountFen(), message.modelName(),
                message.inputTokens(), message.outputTokens(), errorMessage
        );
        MessagePostProcessor setRetryHeader = m -> {
            m.getMessageProperties().setHeader(AgentBillingMqConfig.RETRY_HEADER, nextRetry);
            return m;
        };

        if (currentRetry < MAX_RETRIES) {
            rabbitTemplate.convertAndSend(
                    AgentBillingMqConfig.EXCHANGE,
                    AgentBillingMqConfig.RETRY_ROUTING_KEY,
                    failedMessage,
                    setRetryHeader
            );
            log.warn("agent billing db-sync: routed to retry, username={}, amount={}, retry={}/{}, err={}",
                    message.username(), message.amountFen(), nextRetry, MAX_RETRIES, errorMessage);
            return;
        }

        rabbitTemplate.convertAndSend(
                AgentBillingMqConfig.EXCHANGE,
                AgentBillingMqConfig.DLQ_ROUTING_KEY,
                failedMessage,
                setRetryHeader
        );
        log.error("agent billing db-sync: routed to DLQ, username={}, amount={}, retry={}, err={}",
                message.username(), message.amountFen(), nextRetry, errorMessage);
    }
}
