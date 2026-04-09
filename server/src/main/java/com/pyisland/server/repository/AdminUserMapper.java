package com.pyisland.server.repository;

import com.pyisland.server.entity.AdminUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminUserMapper {

    AdminUser selectByUsername(@Param("username") String username);

    int insert(AdminUser user);

    java.util.List<AdminUser> selectAll();

    int deleteByUsername(@Param("username") String username);

    int count();

    int updateProfile(@Param("username") String username, @Param("password") String password, @Param("avatar") String avatar);

    int updateAvatar(@Param("username") String username, @Param("avatar") String avatar);

    int updateSessionToken(@Param("username") String username, @Param("sessionToken") String sessionToken);
}
