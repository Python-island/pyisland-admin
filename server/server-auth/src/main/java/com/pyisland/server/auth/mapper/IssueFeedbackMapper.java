package com.pyisland.server.auth.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface IssueFeedbackMapper {

    int insertFeedback(@Param("username") String username,
                       @Param("feedbackType") String feedbackType,
                       @Param("title") String title,
                       @Param("content") String content,
                       @Param("contact") String contact,
                       @Param("clientVersion") String clientVersion,
                       @Param("status") String status,
                       @Param("createdAt") LocalDateTime createdAt,
                       @Param("updatedAt") LocalDateTime updatedAt);

    List<Map<String, Object>> listMine(@Param("username") String username,
                                       @Param("status") String status,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);

    long countMine(@Param("username") String username,
                   @Param("status") String status);

    List<Map<String, Object>> listAdmin(@Param("status") String status,
                                        @Param("keyword") String keyword,
                                        @Param("offset") int offset,
                                        @Param("limit") int limit);

    long countAdmin(@Param("status") String status,
                    @Param("keyword") String keyword);

    int resolve(@Param("id") Long id,
                @Param("status") String status,
                @Param("adminReply") String adminReply,
                @Param("resolvedAt") LocalDateTime resolvedAt,
                @Param("updatedAt") LocalDateTime updatedAt);
}
