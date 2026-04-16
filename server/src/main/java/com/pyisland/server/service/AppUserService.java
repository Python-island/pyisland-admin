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
     * @param password 明文密码。
     * @return 注册成功返回用户，已存在时返回 null。
     */
    public AppUser register(String username, String password) {
        AppUser existing = appUserMapper.selectByUsername(username);
        if (existing != null) {
            return null;
        }
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPassword(hashPassword(password));
        user.setCreatedAt(LocalDateTime.now());
        appUserMapper.insert(user);
        return user;
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
