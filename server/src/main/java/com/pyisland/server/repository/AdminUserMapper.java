package com.pyisland.server.repository;

import com.pyisland.server.entity.AdminUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminUserMapper {

    AdminUser selectByUsername(@Param("username") String username);

    int insert(AdminUser user);
}
