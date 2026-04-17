package com.pyisland.server.servicestatus.mapper;

import com.pyisland.server.servicestatus.entity.ServiceStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 接口状态数据访问接口。
 */
@Mapper
public interface ServiceStatusMapper {

    /**
     * 按接口名称查询状态。
     * @param apiName 接口名称。
     * @return 状态实体。
     */
    ServiceStatus selectByApiName(@Param("apiName") String apiName);

    /**
     * 查询全部接口状态。
     * @return 状态列表。
     */
    java.util.List<ServiceStatus> selectAll();

    /**
     * 更新接口状态。
     * @param serviceStatus 状态实体。
     * @return 影响行数。
     */
    int update(ServiceStatus serviceStatus);

    /**
     * 新增接口状态。
     * @param serviceStatus 状态实体。
     * @return 影响行数。
     */
    int insert(ServiceStatus serviceStatus);
}
