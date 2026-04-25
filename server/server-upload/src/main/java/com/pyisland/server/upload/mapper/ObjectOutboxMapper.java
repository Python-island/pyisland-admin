package com.pyisland.server.upload.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 对象复制 Outbox Mapper。
 */
@Mapper
public interface ObjectOutboxMapper {

    int insertIgnore(@Param("eventType") String eventType,
                     @Param("eventKey") String eventKey,
                     @Param("payloadJson") String payloadJson,
                     @Param("status") String status,
                     @Param("retryCount") int retryCount,
                     @Param("nextRetryAt") LocalDateTime nextRetryAt,
                     @Param("lastError") String lastError,
                     @Param("createdAt") LocalDateTime createdAt,
                     @Param("updatedAt") LocalDateTime updatedAt);

    List<Map<String, Object>> listPending(@Param("now") LocalDateTime now,
                                          @Param("limit") int limit);

    int markPublished(@Param("id") Long id,
                      @Param("publishedAt") LocalDateTime publishedAt,
                      @Param("updatedAt") LocalDateTime updatedAt);

    int markRetrying(@Param("id") Long id,
                     @Param("retryCount") int retryCount,
                     @Param("nextRetryAt") LocalDateTime nextRetryAt,
                     @Param("lastError") String lastError,
                     @Param("updatedAt") LocalDateTime updatedAt);

    int markDlq(@Param("id") Long id,
                @Param("retryCount") int retryCount,
                @Param("lastError") String lastError,
                @Param("updatedAt") LocalDateTime updatedAt);
}
