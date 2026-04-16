package com.pyisland.server.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 普通用户实体。
 */
public class AppUser {

    private Long id;
    private String username;
    private String email;
    private String password;
    private String avatar;
    private String gender;
    private String genderCustom;
    private LocalDate birthday;
    private String sessionToken;
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
     * 获取头像地址。
     * @return 头像地址。
     */
    public String getAvatar() {
        return avatar;
    }

    /**
     * 设置头像地址。
     * @param avatar 头像地址。
     */
    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    /**
     * 获取性别。
     * @return 性别标识，取值 male/female/custom/undisclosed。
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
     * 获取会话 token。
     * @return 会话 token。
     */
    public String getSessionToken() {
        return sessionToken;
    }

    /**
     * 设置会话 token。
     * @param sessionToken 会话 token。
     */
    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
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
}
