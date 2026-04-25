package com.pyisland.server.upload.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 对象复制任务 Mapper。
 */
@Mapper
public interface ObjectReplicationTaskMapper {

    int insertIgnore(@Param("taskKey") String taskKey,
                     @Param("bizType") String bizType,
                     @Param("bizId") Long bizId,
                     @Param("bizKey") String bizKey,
                     @Param("fieldName") String fieldName,
                     @Param("objectKey") String objectKey,
                     @Param("sourceProvider") String sourceProvider,
                     @Param("targetProvider") String targetProvider,
                     @Param("sourceUrl") String sourceUrl,
                     @Param("status") String status,
                     @Param("priority") int priority,
                     @Param("maxRetries") int maxRetries,
                     @Param("nextRetryAt") LocalDateTime nextRetryAt,
                     @Param("createdAt") LocalDateTime createdAt,
                     @Param("updatedAt") LocalDateTime updatedAt);

    Long selectIdByTaskKey(@Param("taskKey") String taskKey);

    Map<String, Object> selectById(@Param("id") Long id);

    int markRetrying(@Param("id") Long id,
                     @Param("retryCount") int retryCount,
                     @Param("nextRetryAt") LocalDateTime nextRetryAt,
                     @Param("lastError") String lastError,
                     @Param("updatedAt") LocalDateTime updatedAt);

    int markSuccess(@Param("id") Long id,
                    @Param("targetUrl") String targetUrl,
                    @Param("updatedAt") LocalDateTime updatedAt,
                    @Param("doneAt") LocalDateTime doneAt);

    int markDlq(@Param("id") Long id,
                @Param("retryCount") int retryCount,
                @Param("lastError") String lastError,
                @Param("updatedAt") LocalDateTime updatedAt,
                @Param("doneAt") LocalDateTime doneAt);

    List<Map<String, Object>> listDlqTasks(@Param("limit") int limit);

    int resetFromDlq(@Param("id") Long id,
                     @Param("nextRetryAt") LocalDateTime nextRetryAt,
                     @Param("updatedAt") LocalDateTime updatedAt);

    int insertLog(@Param("taskId") Long taskId,
                  @Param("traceId") String traceId,
                  @Param("attemptNo") int attemptNo,
                  @Param("status") String status,
                  @Param("durationMs") Integer durationMs,
                  @Param("errorMessage") String errorMessage,
                  @Param("createdAt") LocalDateTime createdAt);
}
