package com.pyisland.server.user.mapper;

import com.pyisland.server.user.entity.AnnouncementConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 公告配置数据访问接口。
 */
@Mapper
public interface AnnouncementConfigMapper {

    AnnouncementConfig selectCurrent();

    AnnouncementConfig selectById(@Param("id") Long id);

    int insert(AnnouncementConfig config);

    int update(@Param("id") Long id,
               @Param("title") String title,
               @Param("content") String content,
               @Param("enabled") boolean enabled,
               @Param("startAt") LocalDateTime startAt,
               @Param("endAt") LocalDateTime endAt,
               @Param("updatedBy") String updatedBy,
               @Param("updatedAt") LocalDateTime updatedAt);
}
