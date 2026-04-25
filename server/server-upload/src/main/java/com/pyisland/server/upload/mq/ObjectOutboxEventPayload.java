package com.pyisland.server.upload.mq;

/**
 * 对象复制 Outbox 事件载荷。
 * @param taskId 复制任务 ID。
 * @param queuePriority 投递到 MQ 的优先级（0-9）。
 */
public record ObjectOutboxEventPayload(Long taskId,
                                       Integer queuePriority) {
}
