package com.pyisland.server.user.mapper;

import com.pyisland.server.user.entity.WallpaperAsset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 壁纸市场数据访问接口。
 */
@Mapper
public interface WallpaperMarketMapper {

    int insertAsset(WallpaperAsset asset);

    int insertVersion(@Param("wallpaperId") Long wallpaperId,
                      @Param("versionNo") int versionNo,
                      @Param("originalUrl") String originalUrl,
                      @Param("thumb320Url") String thumb320Url,
                      @Param("thumb720Url") String thumb720Url,
                      @Param("thumb1280Url") String thumb1280Url,
                      @Param("fileSize") Long fileSize,
                      @Param("width") Integer width,
                      @Param("height") Integer height,
                      @Param("operatorName") String operatorName,
                      @Param("reason") String reason,
                      @Param("createdAt") LocalDateTime createdAt);

    int upsertVideoMeta(@Param("wallpaperId") Long wallpaperId,
                        @Param("durationMs") Long durationMs,
                        @Param("frameRate") BigDecimal frameRate,
                        @Param("createdAt") LocalDateTime createdAt,
                        @Param("updatedAt") LocalDateTime updatedAt);

    int deleteVideoMetaByWallpaperId(@Param("wallpaperId") Long wallpaperId);

    Map<String, Object> selectAssetById(@Param("id") Long id);

    List<Map<String, Object>> listVersionAssetUrls(@Param("wallpaperId") Long wallpaperId);

    List<Map<String, Object>> listPublished(@Param("keyword") String keyword,
                                            @Param("type") String type,
                                            @Param("sortBy") String sortBy,
                                            @Param("offset") int offset,
                                            @Param("limit") int limit);

    long countPublished(@Param("keyword") String keyword,
                        @Param("type") String type);

    List<Map<String, Object>> listMine(@Param("ownerUsername") String ownerUsername,
                                       @Param("keyword") String keyword,
                                       @Param("type") String type,
                                       @Param("sortBy") String sortBy,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);

    long countMine(@Param("ownerUsername") String ownerUsername,
                   @Param("keyword") String keyword,
                   @Param("type") String type);

    List<Map<String, Object>> listAdmin(@Param("keyword") String keyword,
                                        @Param("type") String type,
                                        @Param("status") String status,
                                        @Param("offset") int offset,
                                        @Param("limit") int limit);

    int updateOwnerMetadata(@Param("id") Long id,
                            @Param("ownerUsername") String ownerUsername,
                            @Param("title") String title,
                            @Param("description") String description,
                            @Param("type") String type,
                            @Param("tagsText") String tagsText,
                            @Param("updatedAt") LocalDateTime updatedAt);

    int updateAdminMetadata(@Param("id") Long id,
                            @Param("title") String title,
                            @Param("description") String description,
                            @Param("type") String type,
                            @Param("tagsText") String tagsText,
                            @Param("status") String status,
                            @Param("updatedAt") LocalDateTime updatedAt,
                            @Param("publishedAt") LocalDateTime publishedAt);

    int markOwnerDeleted(@Param("id") Long id,
                         @Param("ownerUsername") String ownerUsername,
                         @Param("updatedAt") LocalDateTime updatedAt);

    int markAdminDeleted(@Param("id") Long id,
                         @Param("updatedAt") LocalDateTime updatedAt);

    int replaceOwnerSource(@Param("id") Long id,
                           @Param("ownerUsername") String ownerUsername,
                           @Param("originalUrl") String originalUrl,
                           @Param("thumb320Url") String thumb320Url,
                           @Param("thumb720Url") String thumb720Url,
                           @Param("thumb1280Url") String thumb1280Url,
                           @Param("fileSize") Long fileSize,
                           @Param("width") Integer width,
                           @Param("height") Integer height,
                           @Param("updatedAt") LocalDateTime updatedAt);

    int incrementApplyAndDownload(@Param("id") Long id);

    int incrementDownloadOnly(@Param("id") Long id);

    int countApplyByUser(@Param("wallpaperId") Long wallpaperId,
                         @Param("username") String username);

    int insertApplyLog(@Param("wallpaperId") Long wallpaperId,
                       @Param("username") String username,
                       @Param("ipHash") String ipHash,
                       @Param("userAgent") String userAgent,
                       @Param("action") String action,
                       @Param("createdAt") LocalDateTime createdAt);

    int upsertRating(@Param("wallpaperId") Long wallpaperId,
                     @Param("username") String username,
                     @Param("score") int score,
                     @Param("createdAt") LocalDateTime createdAt,
                     @Param("updatedAt") LocalDateTime updatedAt);

    int deleteRatingById(@Param("id") Long id);

    int recomputeRatingStats(@Param("wallpaperId") Long wallpaperId);

    int insertReport(@Param("wallpaperId") Long wallpaperId,
                     @Param("reporterUsername") String reporterUsername,
                     @Param("reasonType") String reasonType,
                     @Param("reasonDetail") String reasonDetail,
                     @Param("createdAt") LocalDateTime createdAt);

    List<Map<String, Object>> listReports(@Param("status") String status,
                                          @Param("offset") int offset,
                                          @Param("limit") int limit);

    int resolveReport(@Param("id") Long id,
                      @Param("status") String status,
                      @Param("resolverName") String resolverName,
                      @Param("resolutionNote") String resolutionNote,
                      @Param("resolvedAt") LocalDateTime resolvedAt);

    List<Map<String, Object>> listRatings(@Param("wallpaperId") Long wallpaperId,
                                          @Param("offset") int offset,
                                          @Param("limit") int limit);

    int updateStatusByAdmin(@Param("id") Long id,
                            @Param("status") String status,
                            @Param("updatedAt") LocalDateTime updatedAt,
                            @Param("publishedAt") LocalDateTime publishedAt);

    int insertReviewLog(@Param("wallpaperId") Long wallpaperId,
                        @Param("action") String action,
                        @Param("reviewerName") String reviewerName,
                        @Param("reviewerReason") String reviewerReason,
                        @Param("createdAt") LocalDateTime createdAt);
}
