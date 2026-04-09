package com.pyisland.server.entity;

import java.time.LocalDateTime;

/**
 * 管理员用户实体。
 */
public class AdminUser {

    private Long id;
    private String username;
    private String password;
    private String avatar;
    private String sessionToken;
    private LocalDateTime createdAt;

    /**
     * 默认构造函数。
     */
    public AdminUser() {
    }

    /**
     * 获取主键 ID。
     * @return 管理员 ID。
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置主键 ID。
     * @param id 管理员 ID。
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
