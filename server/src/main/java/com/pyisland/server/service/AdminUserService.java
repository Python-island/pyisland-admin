package com.pyisland.server.service;

import com.pyisland.server.entity.AdminUser;
import com.pyisland.server.repository.AdminUserMapper;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * 管理员用户服务。
 */
@Service
public class AdminUserService {

    private final AdminUserMapper adminUserMapper;

    /**
     * 构造管理员服务。
     * @param adminUserMapper 管理员数据访问接口。
     */
    public AdminUserService(AdminUserMapper adminUserMapper) {
        this.adminUserMapper = adminUserMapper;
    }

    /**
     * 校验管理员登录。
     * @param username 用户名。
     * @param password 明文密码。
     * @return 认证成功返回用户，否则返回 null。
     */
    public AdminUser authenticate(String username, String password) {
        AdminUser user = adminUserMapper.selectByUsername(username);
        if (user != null && user.getPassword().equals(hashPassword(password))) {
            return user;
        }
        return null;
    }

    /**
     * 注册管理员。
     * @param username 用户名。
     * @param password 明文密码。
     * @return 注册成功返回用户，已存在时返回 null。
     */
    public AdminUser register(String username, String password) {
        AdminUser existing = adminUserMapper.selectByUsername(username);
        if (existing != null) {
            return null;
        }
        AdminUser user = new AdminUser();
        user.setUsername(username);
        user.setPassword(hashPassword(password));
        user.setCreatedAt(LocalDateTime.now());
        adminUserMapper.insert(user);
        return user;
    }

    /**
     * 查询全部管理员。
     * @return 管理员列表。
     */
    public java.util.List<AdminUser> listAll() {
        return adminUserMapper.selectAll();
    }

    /**
     * 删除管理员。
     * @param username 用户名。
     * @return 是否删除成功。
     */
    public boolean deleteUser(String username) {
        return adminUserMapper.deleteByUsername(username) > 0;
    }

    /**
     * 统计管理员数量。
     * @return 管理员数量。
     */
    public int count() {
        return adminUserMapper.count();
    }

    /**
     * 按用户名查询管理员。
     * @param username 用户名。
     * @return 管理员信息。
     */
    public AdminUser getByUsername(String username) {
        return adminUserMapper.selectByUsername(username);
    }

    /**
     * 更新管理员密码与头像。
     * @param username 用户名。
     * @param newPassword 新密码。
     * @param avatar 头像地址。
     * @return 是否更新成功。
     */
    public boolean updateProfile(String username, String newPassword, String avatar) {
        String hashed = hashPassword(newPassword);
        return adminUserMapper.updateProfile(username, hashed, avatar) > 0;
    }

    /**
     * 仅更新管理员头像。
     * @param username 用户名。
     * @param avatar 头像地址。
     * @return 是否更新成功。
     */
    public boolean updateAvatar(String username, String avatar) {
        return adminUserMapper.updateAvatar(username, avatar) > 0;
    }

    /**
     * 对明文密码进行 SHA-256 哈希。
     * @param password 明文密码。
     * @return 哈希结果。
     */
    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
