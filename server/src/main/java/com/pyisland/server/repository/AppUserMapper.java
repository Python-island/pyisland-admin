package com.pyisland.server.repository;

import com.pyisland.server.entity.AppUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

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
     * 查询全部普通用户。
     * @return 用户列表。
     */
    java.util.List<AppUser> selectAll();

    /**
     * 按用户名删除普通用户。
     * @param username 用户名。
     * @return 影响行数。
     */
    int deleteByUsername(@Param("username") String username);

    /**
     * 统计普通用户数量。
     * @return 用户数量。
     */
    int count();

    /**
     * 更新普通用户密码与头像。
     * @param username 用户名。
     * @param password 密码哈希。
     * @param avatar 头像地址。
     * @return 影响行数。
     */
    int updateProfile(@Param("username") String username, @Param("password") String password, @Param("avatar") String avatar);

    /**
     * 更新普通用户头像。
     * @param username 用户名。
     * @param avatar 头像地址。
     * @return 影响行数。
     */
    int updateAvatar(@Param("username") String username, @Param("avatar") String avatar);

    /**
     * 更新用户会话 token。
     * @param username 用户名。
     * @param sessionToken 会话 token。
     * @return 影响行数。
     */
    int updateSessionToken(@Param("username") String username, @Param("sessionToken") String sessionToken);

    /**
     * 更新用户扩展信息（性别与生日）。
     * @param username 用户名。
     * @param gender 性别标识。
     * @param genderCustom 自定义性别描述。
     * @param birthday 生日。
     * @return 影响行数。
     */
    int updateExtras(@Param("username") String username,
                     @Param("gender") String gender,
                     @Param("genderCustom") String genderCustom,
                     @Param("birthday") LocalDate birthday);
}
