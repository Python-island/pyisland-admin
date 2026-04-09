package com.pyisland.server.repository;

import com.pyisland.server.entity.AdminUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 管理员用户数据访问接口。
 */
@Mapper
public interface AdminUserMapper {

    /**
     * 按用户名查询管理员。
     * @param username 用户名。
     * @return 管理员实体。
     */
    AdminUser selectByUsername(@Param("username") String username);

    /**
     * 新增管理员。
     * @param user 管理员实体。
     * @return 影响行数。
     */
    int insert(AdminUser user);

    /**
     * 查询全部管理员。
     * @return 管理员列表。
     */
    java.util.List<AdminUser> selectAll();

    /**
     * 按用户名删除管理员。
     * @param username 用户名。
     * @return 影响行数。
     */
    int deleteByUsername(@Param("username") String username);

    /**
     * 统计管理员数量。
     * @return 管理员数量。
     */
    int count();

    /**
     * 更新管理员密码与头像。
     * @param username 用户名。
     * @param password 密码哈希。
     * @param avatar 头像地址。
     * @return 影响行数。
     */
    int updateProfile(@Param("username") String username, @Param("password") String password, @Param("avatar") String avatar);

    /**
     * 更新管理员头像。
     * @param username 用户名。
     * @param avatar 头像地址。
     * @return 影响行数。
     */
    int updateAvatar(@Param("username") String username, @Param("avatar") String avatar);

    /**
     * 更新管理员会话 token。
     * @param username 用户名。
     * @param sessionToken 会话 token。
     * @return 影响行数。
     */
    int updateSessionToken(@Param("username") String username, @Param("sessionToken") String sessionToken);
}
