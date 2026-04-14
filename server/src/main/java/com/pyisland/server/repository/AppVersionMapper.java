package com.pyisland.server.repository;

import com.pyisland.server.entity.AppVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 应用版本数据访问接口。
 */
@Mapper
public interface AppVersionMapper {

    /**
     * 按应用名称查询版本。
     * @param appName 应用名称。
     * @return 版本实体。
     */
    AppVersion selectByAppName(@Param("appName") String appName);

    /**
     * 新增版本信息。
     * @param appVersion 版本实体。
     * @return 影响行数。
     */
    int insert(AppVersion appVersion);

    /**
     * 按应用名称更新版本。
     * @param appVersion 版本实体。
     * @return 影响行数。
     */
    int updateByAppName(AppVersion appVersion);

    /**
     * 按应用名称和版本号递增更新次数。
     * @param appName 应用名称。
     * @param version 版本号。
     * @return 影响行数。
     */
    int incrementUpdateCountByAppNameAndVersion(@Param("appName") String appName, @Param("version") String version);

    /**
     * 按应用名称删除版本。
     * @param appName 应用名称。
     * @return 影响行数。
     */
    int deleteByAppName(@Param("appName") String appName);

    /**
     * 查询全部版本。
     * @return 版本列表。
     */
    java.util.List<AppVersion> selectAll();
}
