package com.pyisland.server.upload.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

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
}
