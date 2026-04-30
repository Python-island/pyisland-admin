package com.pyisland.server.user.mapper;

import com.pyisland.server.user.entity.AgentModelPricing;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 模型定价数据访问接口。
 */
@Mapper
public interface AgentModelPricingMapper {

    /**
     * 查询全部定价配置。
     * @return 定价列表。
     */
    List<AgentModelPricing> selectAll();

    /**
     * 按模型名查询。
     * @param modelName 模型名。
     * @return 定价实体或 null。
     */
    AgentModelPricing selectByModelName(@Param("modelName") String modelName);

    /**
     * 新增定价配置。
     * @param pricing 定价实体。
     * @return 影响行数。
     */
    int insert(AgentModelPricing pricing);

    /**
     * 按模型名更新定价。
     * @param modelName 模型名。
     * @param inputPriceFenPerMillion 输入价格（分/百万token）。
     * @param outputPriceFenPerMillion 输出价格（分/百万token）。
     * @param enabled 是否启用。
     * @param updatedAt 更新时间。
     * @return 影响行数。
     */
    int updateByModelName(@Param("modelName") String modelName,
                          @Param("inputPriceFenPerMillion") Long inputPriceFenPerMillion,
                          @Param("outputPriceFenPerMillion") Long outputPriceFenPerMillion,
                          @Param("enabled") Boolean enabled,
                          @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 按模型名删除。
     * @param modelName 模型名。
     * @return 影响行数。
     */
    int deleteByModelName(@Param("modelName") String modelName);
}
