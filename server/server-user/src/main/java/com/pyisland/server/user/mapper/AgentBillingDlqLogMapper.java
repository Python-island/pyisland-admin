package com.pyisland.server.user.mapper;

import com.pyisland.server.user.entity.AgentBillingDlqLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 计费 DLQ 异常记录数据访问接口。
 */
@Mapper
public interface AgentBillingDlqLogMapper {

    /**
     * 新增 DLQ 记录。
     * @param log DLQ 记录实体。
     * @return 影响行数。
     */
    int insert(AgentBillingDlqLog log);

    /**
     * 查询全部 DLQ 记录，按创建时间倒序。
     * @return DLQ 记录列表。
     */
    List<AgentBillingDlqLog> selectAll();

    /**
     * 按状态查询 DLQ 记录。
     * @param status 状态（pending / resolved / ignored）。
     * @return DLQ 记录列表。
     */
    List<AgentBillingDlqLog> selectByStatus(@Param("status") String status);

    /**
     * 更新 DLQ 记录状态（人工处理）。
     * @param id 记录 ID。
     * @param status 新状态。
     * @param resolvedBy 处理人。
     * @param resolvedAt 处理时间。
     * @return 影响行数。
     */
    int updateStatus(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("resolvedBy") String resolvedBy,
                     @Param("resolvedAt") LocalDateTime resolvedAt);

    /**
     * 统计待处理数量。
     * @return 待处理数量。
     */
    int countPending();
}
