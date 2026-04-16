package com.pyisland.server.service;

import com.pyisland.server.entity.AppUser;
import com.pyisland.server.repository.AppUserMapper;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * 普通用户服务。
 */
@Service
public class AppUserService {

    private final AppUserMapper appUserMapper;

    /**
     * 构造普通用户服务。
     * @param appUserMapper 普通用户数据访问接口。
     */
    public AppUserService(AppUserMapper appUserMapper) {
        this.appUserMapper = appUserMapper;
    }

    /**
     * 校验普通用户登录。
     * @param username 用户名。
     * @param password 明文密码。
     * @return 认证成功返回用户，否则返回 null。
     */
    public AppUser authenticate(String username, String password) {
        AppUser user = appUserMapper.selectByUsername(username);
        if (user != null && user.getPassword().equals(hashPassword(password))) {
            return user;
        }
        return null;
    }

    /**
     * 注册普通用户。
     * @param username 用户名。
     * @param email 邮箱。
     * @param password 明文密码。
     * @return 注册成功返回用户，已存在时返回 null。
     */
    public AppUser register(String username, String email, String password) {
        AppUser existing = appUserMapper.selectByUsername(username);
        AppUser existingByEmail = appUserMapper.selectByEmail(email);
        if (existing != null || existingByEmail != null) {
            return null;
        }
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(hashPassword(password));
        user.setCreatedAt(LocalDateTime.now());
        appUserMapper.insert(user);
        return user;
    }

    /**
     * 查询全部普通用户。
     * @return 普通用户列表。
     */
    public java.util.List<AppUser> listAll() {
        return appUserMapper.selectAll();
    }

    /**
     * 删除普通用户。
     * @param username 用户名。
     * @return 是否删除成功。
     */
    public boolean deleteUser(String username) {
        return appUserMapper.deleteByUsername(username) > 0;
    }

    /**
     * 统计普通用户数量。
     * @return 普通用户数量。
     */
    public int count() {
        return appUserMapper.count();
    }

    /**
     * 按用户名查询普通用户。
     * @param username 用户名。
     * @return 普通用户信息。
     */
    public AppUser getByUsername(String username) {
        return appUserMapper.selectByUsername(username);
    }

    /**
     * 更新普通用户密码与头像。
     * @param username 用户名。
     * @param newPassword 新密码。
     * @param avatar 头像地址。
     * @return 是否更新成功。
     */
    public boolean updateProfile(String username, String newPassword, String avatar) {
        String hashed = hashPassword(newPassword);
        return appUserMapper.updateProfile(username, hashed, avatar) > 0;
    }

    /**
     * 仅更新普通用户头像。
     * @param username 用户名。
     * @param avatar 头像地址。
     * @return 是否更新成功。
     */
    public boolean updateAvatar(String username, String avatar) {
        return appUserMapper.updateAvatar(username, avatar) > 0;
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
