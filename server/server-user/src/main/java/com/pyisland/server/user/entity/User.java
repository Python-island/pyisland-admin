package com.pyisland.server.user.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 统一用户实体：合并了原 admin_user 与 app_user，通过 role 字段区分角色。
 */
public class User {

    /** 普通用户角色。 */
    public static final String ROLE_USER = "user";
    /** Pro 用户角色。 */
    public static final String ROLE_PRO = "pro";
    /** 管理员角色。 */
    public static final String ROLE_ADMIN = "admin";

    private Long id;
    private String username;
    private String email;
    private String password;
    private String role;
    private LocalDateTime proExpireAt;
    private String avatar;
    private String gender;
    private String genderCustom;
    private LocalDate birthday;
    private Boolean enabled;
    private String sessionToken;
    private String totpSecretCiphertext;
    private LocalDateTime totpSecretUpdatedAt;
    private Long balanceFen;
    private LocalDateTime createdAt;

    /**
     * 获取主键 ID。
     * @return 用户 ID。
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置主键 ID。
     * @param id 用户 ID。
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取用户名。
     * @return 用户名。
     */
    public String getUsername() {
        return username;
    }

    /**
     * 设置用户名。
     * @param username 用户名。
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * 获取邮箱。
     * @return 邮箱。
     */
    public String getEmail() {
        return email;
    }

    /**
     * 设置邮箱。
     * @param email 邮箱。
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * 获取密码哈希。
     * @return 密码哈希。
     */
    public String getPassword() {
        return password;
    }

    /**
     * 设置密码哈希。
     * @param password 密码哈希。
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * 获取角色。
     * @return 角色，取值 admin/pro/user。
     */
    public String getRole() {
        return role;
    }

    /**
     * 设置角色。
     * @param role 角色。
     */
    public void setRole(String role) {
        this.role = role;
    }

    /**
     * 获取 Pro 到期时间。
     * @return Pro 到期时间。
     */
    public LocalDateTime getProExpireAt() {
        return proExpireAt;
    }

    /**
     * 设置 Pro 到期时间。
     * @param proExpireAt Pro 到期时间。
     */
    public void setProExpireAt(LocalDateTime proExpireAt) {
        this.proExpireAt = proExpireAt;
    }

    /**
     * 获取头像地址。
     * @return 头像 URL。
     */
    public String getAvatar() {
        return avatar;
    }

    /**
     * 设置头像地址。
     * @param avatar 头像 URL。
     */
    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    /**
     * 获取性别。
     * @return 性别标识。
     */
    public String getGender() {
        return gender;
    }

    /**
     * 设置性别。
     * @param gender 性别标识。
     */
    public void setGender(String gender) {
        this.gender = gender;
    }

    /**
     * 获取自定义性别描述。
     * @return 自定义性别描述。
     */
    public String getGenderCustom() {
        return genderCustom;
    }

    /**
     * 设置自定义性别描述。
     * @param genderCustom 自定义性别描述。
     */
    public void setGenderCustom(String genderCustom) {
        this.genderCustom = genderCustom;
    }

    /**
     * 获取生日。
     * @return 生日。
     */
    public LocalDate getBirthday() {
        return birthday;
    }

    /**
     * 设置生日。
     * @param birthday 生日。
     */
    public void setBirthday(LocalDate birthday) {
        this.birthday = birthday;
    }

    /**
     * 获取账号是否启用。
     * @return 启用时为 true。
     */
    public Boolean getEnabled() {
        return enabled;
    }

    /**
     * 设置账号启用状态。
     * @param enabled 启用时为 true。
     */
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取会话 token。
     * @return 当前生效的 JWT。
     */
    public String getSessionToken() {
        return sessionToken;
    }

    /**
     * 设置会话 token。
     * @param sessionToken 当前生效的 JWT。
     */
    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    /**
     * 获取 TOTP 种子密文。
     * @return AES-GCM 加密后的 Base64 密文。
     */
    public String getTotpSecretCiphertext() {
        return totpSecretCiphertext;
    }

    /**
     * 设置 TOTP 种子密文。
     * @param totpSecretCiphertext AES-GCM 加密后的 Base64 密文。
     */
    public void setTotpSecretCiphertext(String totpSecretCiphertext) {
        this.totpSecretCiphertext = totpSecretCiphertext;
    }

    /**
     * 获取 TOTP 种子更新时间。
     * @return 种子最近更新时间。
     */
    public LocalDateTime getTotpSecretUpdatedAt() {
        return totpSecretUpdatedAt;
    }

    /**
     * 设置 TOTP 种子更新时间。
     * @param totpSecretUpdatedAt 种子最近更新时间。
     */
    public void setTotpSecretUpdatedAt(LocalDateTime totpSecretUpdatedAt) {
        this.totpSecretUpdatedAt = totpSecretUpdatedAt;
    }

    /**
     * 获取余额（分）。
     * @return 余额，单位：分。
     */
    public Long getBalanceFen() {
        return balanceFen;
    }

    /**
     * 设置余额（分）。
     * @param balanceFen 余额，单位：分。
     */
    public void setBalanceFen(Long balanceFen) {
        this.balanceFen = balanceFen;
    }

    /**
     * 获取创建时间。
     * @return 创建时间。
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * 设置创建时间。
     * @param createdAt 创建时间。
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * 判断是否为管理员。
     * @return true 表示 admin 角色。
     */
    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role);
    }
}
