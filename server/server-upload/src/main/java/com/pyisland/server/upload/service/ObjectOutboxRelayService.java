package com.pyisland.server.upload.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pyisland.server.upload.config.ObjectReplicationMqConfig;
import com.pyisland.server.upload.mapper.ObjectOutboxMapper;
import com.pyisland.server.upload.mq.ObjectOutboxEventPayload;
import com.pyisland.server.upload.mq.ObjectReplicationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 对象复制 Outbox Relay 服务。
 */
@Service
public class ObjectOutboxRelayService {

    private static final Logger log = LoggerFactory.getLogger(ObjectOutboxRelayService.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ObjectOutboxMapper objectOutboxMapper;
    private final RabbitTemplate rabbitTemplate;
    private final boolean replicationEnabled;
    private final boolean outboxRelayEnabled;
    private final int relayBatchSize;
    private final int maxRetries;
    private final long retryDelayMs;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public ObjectOutboxRelayService(ObjectOutboxMapper objectOutboxMapper,
                                    RabbitTemplate rabbitTemplate,
                                    @Value("${object-replication.enabled:true}") boolean replicationEnabled,
                                    @Value("${object-replication.outbox-relay-enabled:true}") boolean outboxRelayEnabled,
                                    @Value("${object-replication.outbox-relay-batch-size:100}") int relayBatchSize,
                                    @Value("${object-replication.max-retries:6}") int maxRetries,
                                    @Value("${object-replication.retry-delay-ms:15000}") long retryDelayMs) {
        this.objectOutboxMapper = objectOutboxMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.replicationEnabled = replicationEnabled;
        this.outboxRelayEnabled = outboxRelayEnabled;
        this.relayBatchSize = Math.max(1, relayBatchSize);
        this.maxRetries = Math.max(1, maxRetries);
        this.retryDelayMs = Math.max(1000L, retryDelayMs);
    }

    @Scheduled(fixedDelayString = "${object-replication.outbox-relay-interval-ms:3000}")
    public void relayPendingOutboxEvents() {
        if (!replicationEnabled || !outboxRelayEnabled) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            LocalDateTime now = LocalDateTime.now();
            List<Map<String, Object>> rows = objectOutboxMapper.listPending(now, relayBatchSize);
            for (Map<String, Object> row : rows) {
                handleSingleOutboxRow(row);
            }
        } finally {
            running.set(false);
        }
    }

    private void handleSingleOutboxRow(Map<String, Object> row) {
        Long outboxId = parseLong(row.get("id"));
        if (outboxId == null || outboxId <= 0) {
            return;
        }
        String eventType = safeText(row.get("eventType"), 80);
        if (!ObjectReplicationTaskService.OUTBOX_EVENT_TYPE_REPLICATION_PUBLISH.equals(eventType)) {
            objectOutboxMapper.markDlq(
                    outboxId,
                    parseInt(row.get("retryCount"), 0),
                    "unsupported outbox event type: " + eventType,
                    LocalDateTime.now()
            );
            return;
        }

        try {
            ObjectOutboxEventPayload payload = parsePayload(safeText(row.get("payloadJson"), 5000));
            Long taskId = payload.taskId();
            if (taskId == null || taskId <= 0) {
                throw new IllegalArgumentException("invalid taskId in outbox payload");
            }
            int mqPriority = normalizePriority(payload.queuePriority());
            String traceId = UUID.randomUUID().toString().replace("-", "");
            ObjectReplicationMessage message = new ObjectReplicationMessage(taskId, traceId, "");
            MessagePostProcessor postProcessor = msg -> {
                msg.getMessageProperties().setHeader(ObjectReplicationMqConfig.RETRY_HEADER, 0);
                msg.getMessageProperties().setPriority(mqPriority);
                return msg;
            };
            rabbitTemplate.convertAndSend(
                    ObjectReplicationMqConfig.REPLICATION_EXCHANGE,
                    ObjectReplicationMqConfig.REPLICATION_ROUTING_KEY,
                    message,
                    postProcessor
            );
            LocalDateTime now = LocalDateTime.now();
            objectOutboxMapper.markPublished(outboxId, now, now);
            log.debug("object outbox published id={} taskId={} priority={}", outboxId, taskId, mqPriority);
        } catch (Exception ex) {
            int retryCount = parseInt(row.get("retryCount"), 0) + 1;
            String errorMessage = safeText(ex.getMessage(), 500);
            LocalDateTime now = LocalDateTime.now();
            if (retryCount > maxRetries) {
                objectOutboxMapper.markDlq(outboxId, retryCount, errorMessage, now);
                log.error("object outbox routed to dlq id={} retry={} err={}", outboxId, retryCount, errorMessage);
                return;
            }
            objectOutboxMapper.markRetrying(
                    outboxId,
                    retryCount,
                    now.plusNanos(retryDelayMs * 1_000_000),
                    errorMessage,
                    now
            );
            log.warn("object outbox retry scheduled id={} retry={}/{} err={}",
                    outboxId,
                    retryCount,
                    maxRetries,
                    errorMessage);
        }
    }

    private ObjectOutboxEventPayload parsePayload(String payloadJson) throws Exception {
        return OBJECT_MAPPER.readValue(payloadJson, ObjectOutboxEventPayload.class);
    }

    private int normalizePriority(Integer value) {
        if (value == null) {
            return 5;
        }
        if (value < 0) {
            return 0;
        }
        return Math.min(value, 9);
    }

    private String safeText(Object value, int maxLen) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).trim();
        if (text.length() > maxLen) {
            return text.substring(0, maxLen);
        }
        return text;
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private int parseInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }
}
