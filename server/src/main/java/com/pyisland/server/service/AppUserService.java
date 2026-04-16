package com.pyisland.server.service;

import com.pyisland.server.entity.AppUser;
import com.pyisland.server.repository.AppUserMapper;
import com.pyisland.server.security.PasswordHashService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 普通用户服务。
 */
@Service
public class AppUserService {

    private final AppUserMapper appUserMapper;
    private final PasswordHashService passwordHashService;

    /**
     * 构造普通用户服务。
     * @param appUserMapper 普通用户数据访问接口。
     * @param passwordHashService 密码哈希服务。
     */
    public AppUserService(AppUserMapper appUserMapper,
                          PasswordHashService passwordHashService) {
        this.appUserMapper = appUserMapper;
        this.passwordHashService = passwordHashService;
    }

    /**
     * 校验普通用户登录。登录成功时若仍是旧 SHA-256 哈希则自动升级为 BCrypt。
     * @param username 用户名。
     * @param password 明文密码。
     * @return 认证成功返回用户，否则返回 null。
     */
    public AppUser authenticate(String username, String password) {
        AppUser user = appUserMapper.selectByUsername(username);
        if (user == null) {
            return null;
        }
        if (!passwordHashService.matches(password, user.getPassword())) {
            return null;
        }
        if (!passwordHashService.isBcrypt(user.getPassword())) {
            String upgraded = passwordHashService.hash(password);
            appUserMapper.updateProfile(user.getUsername(), upgraded, user.getAvatar());
            user.setPassword(upgraded);
        }
        return user;
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
        user.setPassword(passwordHashService.hash(password));
        user.setGender("undisclosed");
        user.setCreatedAt(LocalDateTime.now());
        appUserMapper.insert(user);
        return user;
    }

    /**
     * 更新普通用户性别与生日。
     * @param username 用户名。
     * @param gender 性别标识。
     * @param genderCustom 自定义性别描述（仅在 gender=custom 时生效）。
     * @param birthday 生日。
     * @return 是否更新成功。
     */
    public boolean updateExtras(String username, String gender, String genderCustom, LocalDate birthday) {
        return appUserMapper.updateExtras(username, gender, genderCustom, birthday) > 0;
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
        String hashed = passwordHashService.hash(newPassword);
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
}
