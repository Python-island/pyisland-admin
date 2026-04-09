package com.pyisland.server.repository;

import com.pyisland.server.entity.AppVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppVersionMapper {

    AppVersion selectByAppName(@Param("appName") String appName);

    int insert(AppVersion appVersion);

    int updateByAppName(AppVersion appVersion);
}
