package com.pyisland.server.repository;

import com.pyisland.server.entity.AppUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 普通用户数据访问接口。
 */
@Mapper
public interface AppUserMapper {

    /**
     * 按用户名查询普通用户。
     * @param username 用户名。
     * @return 用户实体。
     */
    AppUser selectByUsername(@Param("username") String username);

    /**
     * 按邮箱查询普通用户。
     * @param email 邮箱。
     * @return 用户实体。
     */
    AppUser selectByEmail(@Param("email") String email);

    /**
     * 新增普通用户。
     * @param user 用户实体。
     * @return 影响行数。
     */
    int insert(AppUser user);

    /**
     * 更新用户会话 token。
     * @param username 用户名。
     * @param sessionToken 会话 token。
     * @return 影响行数。
     */
    int updateSessionToken(@Param("username") String username, @Param("sessionToken") String sessionToken);
}
