package com.pyisland.server.repository;

import com.pyisland.server.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 统一用户数据访问接口。
 */
@Mapper
public interface UserMapper {

    /**
     * 按用户名查询。
     * @param username 用户名。
     * @return 用户实体或 null。
     */
    User selectByUsername(@Param("username") String username);

    /**
     * 按邮箱查询。
     * @param email 邮箱。
     * @return 用户实体或 null。
     */
    User selectByEmail(@Param("email") String email);

    /**
     * 按 ID 查询。
     * @param id 用户 ID。
     * @return 用户实体或 null。
     */
    User selectById(@Param("id") Long id);

    /**
     * 新增用户。
     * @param user 用户实体。
     * @return 影响行数。
     */
    int insert(User user);

    /**
     * 按角色查询全部用户。
     * @param role 角色，传 null 表示全部。
     * @return 用户列表，按创建时间倒序。
     */
    List<User> selectByRole(@Param("role") String role);

    /**
     * 按用户名删除。
     * @param username 用户名。
     * @return 影响行数。
     */
    int deleteByUsername(@Param("username") String username);

    /**
     * 按角色统计数量。
     * @param role 角色，传 null 表示全部。
     * @return 数量。
     */
    int countByRole(@Param("role") String role);

    /**
     * 更新密码与头像。
     * @param username 用户名。
     * @param password 新密码哈希。
     * @param avatar 头像 URL。
     * @return 影响行数。
     */
    int updateProfile(@Param("username") String username,
                      @Param("password") String password,
                      @Param("avatar") String avatar);

    /**
     * 仅更新头像。
     * @param username 用户名。
     * @param avatar 头像 URL。
     * @return 影响行数。
     */
    int updateAvatar(@Param("username") String username,
                     @Param("avatar") String avatar);

    /**
     * 更新扩展资料：性别与生日。
     * @param username 用户名。
     * @param gender 性别标识。
     * @param genderCustom 自定义性别。
     * @param birthday 生日。
     * @return 影响行数。
     */
    int updateExtras(@Param("username") String username,
                     @Param("gender") String gender,
                     @Param("genderCustom") String genderCustom,
                     @Param("birthday") LocalDate birthday);

    /**
     * 更新会话 token。
     * @param username 用户名。
     * @param sessionToken token 值，传 null 表示清空。
     * @return 影响行数。
     */
    int updateSessionToken(@Param("username") String username,
                           @Param("sessionToken") String sessionToken);

    /**
     * 更新角色。
     * @param username 用户名。
     * @param role 新角色。
     * @return 影响行数。
     */
    int updateRole(@Param("username") String username,
                   @Param("role") String role);

    /**
     * 更新启用状态。
     * @param username 用户名。
     * @param enabled 启用时为 true。
     * @return 影响行数。
     */
    int updateEnabled(@Param("username") String username,
                      @Param("enabled") boolean enabled);

    /**
     * 更新邮箱。
     * @param username 用户名。
     * @param email 新邮箱。
     * @return 影响行数。
     */
    int updateEmail(@Param("username") String username,
                    @Param("email") String email);
}
