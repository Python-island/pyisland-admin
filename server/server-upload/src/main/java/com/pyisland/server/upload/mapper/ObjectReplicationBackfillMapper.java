package com.pyisland.server.upload.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 对象复制全量迁移数据源 Mapper。
 */
@Mapper
public interface ObjectReplicationBackfillMapper {

    List<Map<String, Object>> listUserAvatarRows(@Param("afterId") long afterId,
                                                 @Param("limit") int limit);

    List<Map<String, Object>> listWallpaperAssetRows(@Param("afterId") long afterId,
                                                     @Param("limit") int limit);

    List<Map<String, Object>> listIssueFeedbackRows(@Param("afterId") long afterId,
                                                    @Param("limit") int limit);
}
