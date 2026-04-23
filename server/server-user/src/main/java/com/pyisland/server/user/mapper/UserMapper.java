package com.pyisland.server.user.mapper;

import com.pyisland.server.user.entity.UserDailyActiveStat;
import com.pyisland.server.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
     * 仅更新密码。
     * @param username 用户名。
     * @param password 新密码哈希。
     * @return 影响行数。
     */
    int updatePassword(@Param("username") String username,
                       @Param("password") String password);

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
     * 更新用户 TOTP 种子密文。
     * @param username 用户名。
     * @param totpSecretCiphertext AES-GCM 密文（Base64）。
     * @param updatedAt 更新时间。
     * @return 影响行数。
     */
    int updateTotpSecret(@Param("username") String username,
                         @Param("totpSecretCiphertext") String totpSecretCiphertext,
                         @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 更新角色。
     * @param username 用户名。
     * @param role 新角色。
     * @return 影响行数。
     */
    int updateRole(@Param("username") String username,
                   @Param("role") String role);

    /**
     * 更新 Pro 到期时间。
     * @param username 用户名。
     * @param proExpireAt Pro 到期时间。
     * @return 影响行数。
     */
    int updateProExpireAt(@Param("username") String username,
                          @Param("proExpireAt") LocalDateTime proExpireAt);

    /**
     * 将已过期的 Pro 用户降级为普通用户。
     * @param now 当前时间。
     * @return 影响行数。
     */
    int demoteExpiredProUsers(@Param("now") LocalDateTime now);

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

    /**
     * 写入日活跃记录（同一用户同一角色同一天仅保留一条）。
     * @param username 用户名。
     * @param role 角色。
     * @param activeDate 活跃日期。
     * @param activeAt 活跃时间。
     * @return 影响行数。
     */
    int insertDailyActive(@Param("username") String username,
                          @Param("role") String role,
                          @Param("activeDate") LocalDate activeDate,
                          @Param("activeAt") LocalDateTime activeAt);

    /**
     * 统计指定日期活跃用户数。
     * @param activeDate 活跃日期。
     * @param role 角色。
     * @return 活跃用户数。
     */
    long countDailyActive(@Param("activeDate") LocalDate activeDate,
                          @Param("role") String role);

    /**
     * 查询日期区间内日活跃统计。
     * @param startDate 开始日期（含）。
     * @param endDate 结束日期（含）。
     * @param role 角色。
     * @return 统计列表。
     */
    List<UserDailyActiveStat> selectDailyActiveRange(@Param("startDate") LocalDate startDate,
                                                     @Param("endDate") LocalDate endDate,
                                                     @Param("role") String role);
}
