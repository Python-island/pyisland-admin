package com.pyisland.server.upload.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 对象复制全量迁移检查点 Mapper。
 */
@Mapper
public interface ObjectReplicationCheckpointMapper {

    int insertIgnore(@Param("checkpointKey") String checkpointKey,
                     @Param("lastId") long lastId,
                     @Param("status") String status,
                     @Param("lastError") String lastError,
                     @Param("doneAt") LocalDateTime doneAt,
                     @Param("createdAt") LocalDateTime createdAt,
                     @Param("updatedAt") LocalDateTime updatedAt);

    Map<String, Object> selectByKey(@Param("checkpointKey") String checkpointKey);

    int markRunning(@Param("checkpointKey") String checkpointKey,
                    @Param("lastId") long lastId,
                    @Param("lastError") String lastError,
                    @Param("updatedAt") LocalDateTime updatedAt);

    int markDone(@Param("checkpointKey") String checkpointKey,
                 @Param("lastId") long lastId,
                 @Param("lastError") String lastError,
                 @Param("doneAt") LocalDateTime doneAt,
                 @Param("updatedAt") LocalDateTime updatedAt);
}
