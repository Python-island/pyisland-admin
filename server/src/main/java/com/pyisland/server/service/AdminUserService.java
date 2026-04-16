package com.pyisland.server.service;

import com.pyisland.server.entity.AdminUser;
import com.pyisland.server.repository.AdminUserMapper;
import com.pyisland.server.security.PasswordHashService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 管理员用户服务。
 */
@Service
public class AdminUserService {

    private final AdminUserMapper adminUserMapper;
    private final PasswordHashService passwordHashService;

    /**
     * 构造管理员服务。
     * @param adminUserMapper 管理员数据访问接口。
     * @param passwordHashService 密码哈希服务。
     */
    public AdminUserService(AdminUserMapper adminUserMapper,
                            PasswordHashService passwordHashService) {
        this.adminUserMapper = adminUserMapper;
        this.passwordHashService = passwordHashService;
    }

    /**
     * 校验管理员登录。登录成功时若仍是旧 SHA-256 哈希则自动升级为 BCrypt。
     * @param username 用户名。
     * @param password 明文密码。
     * @return 认证成功返回用户，否则返回 null。
     */
    public AdminUser authenticate(String username, String password) {
        AdminUser user = adminUserMapper.selectByUsername(username);
        if (user == null) {
            return null;
        }
        if (!passwordHashService.matches(password, user.getPassword())) {
            return null;
        }
        if (!passwordHashService.isBcrypt(user.getPassword())) {
            String upgraded = passwordHashService.hash(password);
            adminUserMapper.updateProfile(user.getUsername(), upgraded, user.getAvatar());
            user.setPassword(upgraded);
        }
        return user;
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
        user.setPassword(passwordHashService.hash(password));
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
        String hashed = passwordHashService.hash(newPassword);
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
}
