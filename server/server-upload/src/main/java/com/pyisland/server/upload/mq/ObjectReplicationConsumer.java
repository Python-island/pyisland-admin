package com.pyisland.server.upload.mq;

import com.pyisland.server.upload.config.ObjectReplicationMqConfig;
import com.pyisland.server.upload.service.ObjectReplicationTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 对象复制任务消费者。
 */
@Component
public class ObjectReplicationConsumer {

    private static final Logger log = LoggerFactory.getLogger(ObjectReplicationConsumer.class);

    private final ObjectReplicationTaskService objectReplicationTaskService;
    private final RabbitTemplate rabbitTemplate;
    private final int maxRetries;
    private final long retryDelayMs;

    public ObjectReplicationConsumer(ObjectReplicationTaskService objectReplicationTaskService,
                                     RabbitTemplate rabbitTemplate,
                                     @Value("${object-replication.max-retries:6}") int maxRetries,
                                     @Value("${object-replication.retry-delay-ms:15000}") long retryDelayMs) {
        this.objectReplicationTaskService = objectReplicationTaskService;
        this.rabbitTemplate = rabbitTemplate;
        this.maxRetries = Math.max(1, maxRetries);
        this.retryDelayMs = Math.max(1000L, retryDelayMs);
    }

    @RabbitListener(queues = ObjectReplicationMqConfig.REPLICATION_QUEUE)
    public void onMessage(ObjectReplicationMessage message,
                          @Header(name = ObjectReplicationMqConfig.RETRY_HEADER, required = false) Integer retryCount) {
        if (message == null || message.taskId() == null || message.taskId() <= 0) {
            return;
        }
        int currentRetry = retryCount == null ? 0 : Math.max(0, retryCount);
        int attemptNo = currentRetry + 1;
        String traceId = normalizeTraceId(message.traceId());
        ObjectReplicationTaskService.ProcessResult result = objectReplicationTaskService.processTask(
                message.taskId(),
                traceId,
                attemptNo
        );
        if (result.success()) {
            log.info("object replication success taskId={} traceId={} attempt={} durationMs={} targetUrl={}",
                    message.taskId(),
                    traceId,
                    attemptNo,
                    result.durationMs(),
                    result.targetUrl());
            return;
        }

        int allowedRetries = Math.max(1, Math.min(maxRetries, result.maxRetries()));
        routeToRetryOrDlq(message, traceId, currentRetry, allowedRetries, result.errorMessage());
    }

    @RabbitListener(queues = ObjectReplicationMqConfig.REPLICATION_DLQ)
    public void onDeadLetter(ObjectReplicationMessage message,
                             @Header(name = ObjectReplicationMqConfig.RETRY_HEADER, required = false) Integer retryCount) {
        if (message == null || message.taskId() == null || message.taskId() <= 0) {
            return;
        }
        int deadRetry = retryCount == null ? 0 : Math.max(0, retryCount);
        String traceId = normalizeTraceId(message.traceId());
        objectReplicationTaskService.markDlq(message.taskId(), deadRetry, message.lastError());
        objectReplicationTaskService.recordAttemptLog(
                message.taskId(),
                traceId,
                Math.max(1, deadRetry + 1),
                "dlq",
                null,
                message.lastError()
        );
        log.error("object replication entered dlq taskId={} traceId={} retry={} err={}",
                message.taskId(),
                traceId,
                deadRetry,
                message.lastError());
    }

    private void routeToRetryOrDlq(ObjectReplicationMessage message,
                                   String traceId,
                                   int currentRetry,
                                   int allowedRetries,
                                   String errorMessage) {
        int nextRetry = currentRetry + 1;
        String safeError = errorMessage == null ? "unknown" : errorMessage;
        ObjectReplicationMessage failed = new ObjectReplicationMessage(message.taskId(), traceId, safeError);
        MessagePostProcessor setRetryHeader = msg -> {
            msg.getMessageProperties().setHeader(ObjectReplicationMqConfig.RETRY_HEADER, nextRetry);
            msg.getMessageProperties().setPriority(5);
            return msg;
        };

        if (currentRetry < allowedRetries) {
            objectReplicationTaskService.markRetrying(
                    message.taskId(),
                    nextRetry,
                    LocalDateTime.now().plusNanos(retryDelayMs * 1_000_000),
                    safeError
            );
            rabbitTemplate.convertAndSend(
                    ObjectReplicationMqConfig.REPLICATION_EXCHANGE,
                    ObjectReplicationMqConfig.REPLICATION_RETRY_ROUTING_KEY,
                    failed,
                    setRetryHeader
            );
            log.warn("object replication routed to retry taskId={} traceId={} retry={}/{} err={}",
                    message.taskId(),
                    traceId,
                    nextRetry,
                    allowedRetries,
                    safeError);
            return;
        }

        objectReplicationTaskService.markDlq(message.taskId(), nextRetry, safeError);
        rabbitTemplate.convertAndSend(
                ObjectReplicationMqConfig.REPLICATION_EXCHANGE,
                ObjectReplicationMqConfig.REPLICATION_DLQ_ROUTING_KEY,
                failed,
                setRetryHeader
        );
        log.error("object replication routed to dlq taskId={} traceId={} retry={} err={}",
                message.taskId(),
                traceId,
                nextRetry,
                safeError);
    }

    private String normalizeTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return UUID.randomUUID().toString().replace("-", "");
        }
        return traceId;
    }
}
