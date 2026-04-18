package com.pyisland.server.user.mapper;

import com.pyisland.server.user.entity.WallpaperTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 壁纸标签数据访问接口。
 */
@Mapper
public interface WallpaperTagMapper {

    int insertTag(WallpaperTag tag);

    WallpaperTag selectBySlug(@Param("slug") String slug);

    WallpaperTag selectById(@Param("id") Long id);

    List<Map<String, Object>> searchByKeyword(@Param("keyword") String keyword,
                                              @Param("limit") int limit);

    List<Map<String, Object>> listAdmin(@Param("keyword") String keyword,
                                        @Param("enabled") Integer enabled,
                                        @Param("offset") int offset,
                                        @Param("limit") int limit);

    long countAdmin(@Param("keyword") String keyword,
                    @Param("enabled") Integer enabled);

    int updateName(@Param("id") Long id,
                   @Param("name") String name,
                   @Param("slug") String slug,
                   @Param("updatedAt") LocalDateTime updatedAt);

    int updateEnabled(@Param("id") Long id,
                      @Param("enabled") boolean enabled,
                      @Param("updatedAt") LocalDateTime updatedAt);

    int deleteTag(@Param("id") Long id);

    int recomputeUsageCount(@Param("id") Long id);

    int insertRef(@Param("wallpaperId") Long wallpaperId,
                  @Param("tagId") Long tagId,
                  @Param("createdAt") LocalDateTime createdAt);

    int deleteRefsByWallpaper(@Param("wallpaperId") Long wallpaperId);

    int deleteRefsByTag(@Param("tagId") Long tagId);

    List<Long> listTagIdsByWallpaper(@Param("wallpaperId") Long wallpaperId);

    List<Map<String, Object>> listTagsByWallpaper(@Param("wallpaperId") Long wallpaperId);
}
