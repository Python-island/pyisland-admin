package com.pyisland.server.upload.mq;

/**
 * 对象复制任务消息。
 * @param taskId 复制任务 ID。
 * @param traceId 链路追踪 ID。
 * @param lastError 上次错误信息。
 */
public record ObjectReplicationMessage(Long taskId,
                                       String traceId,
                                       String lastError) implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
}
