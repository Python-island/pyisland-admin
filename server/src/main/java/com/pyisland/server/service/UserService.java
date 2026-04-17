package com.pyisland.server.service;

import com.pyisland.server.entity.User;
import com.pyisland.server.repository.UserMapper;
import com.pyisland.server.security.PasswordHashService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 统一用户服务。登录/注册/资料管理/角色调整集中于此。
 */
@Service
public class UserService {

    private final UserMapper userMapper;
    private final PasswordHashService passwordHashService;

    /**
     * 构造用户服务。
     * @param userMapper 用户数据访问。
     * @param passwordHashService 密码哈希服务。
     */
    public UserService(UserMapper userMapper,
                       PasswordHashService passwordHashService) {
        this.userMapper = userMapper;
        this.passwordHashService = passwordHashService;
    }

    /**
     * 校验登录。成功时若仍使用旧 SHA-256 哈希则自动升级为 BCrypt。
     * @param username 用户名。
     * @param password 明文密码。
     * @return 认证成功返回用户实体，否则返回 null。
     */
    public User authenticate(String username, String password) {
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            return null;
        }
        if (!passwordHashService.matches(password, user.getPassword())) {
            return null;
        }
        if (Boolean.FALSE.equals(user.getEnabled())) {
            return null;
        }
        if (!passwordHashService.isBcrypt(user.getPassword())) {
            String upgraded = passwordHashService.hash(password);
            userMapper.updateProfile(user.getUsername(), upgraded, user.getAvatar());
            user.setPassword(upgraded);
        }
        return user;
    }

    /**
     * 按用户名查询。
     * @param username 用户名。
     * @return 用户实体或 null。
     */
    public User getByUsername(String username) {
        return userMapper.selectByUsername(username);
    }

    /**
     * 按邮箱查询。
     * @param email 邮箱。
     * @return 用户实体或 null。
     */
    public User getByEmail(String email) {
        return userMapper.selectByEmail(email);
    }

    /**
     * 注册新用户。
     * @param username 用户名。
     * @param email 邮箱。
     * @param password 明文密码。
     * @param role 角色（admin / user）。
     * @return 注册成功返回用户；用户名或邮箱冲突时返回 null。
     */
    public User register(String username, String email, String password, String role) {
        if (userMapper.selectByUsername(username) != null) {
            return null;
        }
        if (email != null && userMapper.selectByEmail(email) != null) {
            return null;
        }
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordHashService.hash(password));
        user.setRole(role != null ? role : User.ROLE_USER);
        user.setGender("undisclosed");
        user.setEnabled(Boolean.TRUE);
        user.setCreatedAt(LocalDateTime.now());
        userMapper.insert(user);
        return user;
    }

    /**
     * 列出指定角色的所有用户（按创建时间倒序）。
     * @param role 角色，传 null 表示全部。
     * @return 用户列表。
     */
    public List<User> listByRole(String role) {
        return userMapper.selectByRole(role);
    }

    /**
     * 统计指定角色数量。
     * @param role 角色，传 null 表示全部。
     * @return 数量。
     */
    public int countByRole(String role) {
        return userMapper.countByRole(role);
    }

    /**
     * 删除用户。
     * @param username 用户名。
     * @return 是否成功。
     */
    public boolean deleteByUsername(String username) {
        return userMapper.deleteByUsername(username) > 0;
    }

    /**
     * 更新密码与头像。
     * @param username 用户名。
     * @param rawPassword 明文新密码。
     * @param avatar 头像 URL。
     * @return 是否成功。
     */
    public boolean updateProfile(String username, String rawPassword, String avatar) {
        String hashed = passwordHashService.hash(rawPassword);
        return userMapper.updateProfile(username, hashed, avatar) > 0;
    }

    /**
     * 仅更新头像。
     * @param username 用户名。
     * @param avatar 头像 URL。
     * @return 是否成功。
     */
    public boolean updateAvatar(String username, String avatar) {
        return userMapper.updateAvatar(username, avatar) > 0;
    }

    /**
     * 更新扩展资料。
     * @param username 用户名。
     * @param gender 性别。
     * @param genderCustom 自定义性别。
     * @param birthday 生日。
     * @return 是否成功。
     */
    public boolean updateExtras(String username, String gender, String genderCustom, LocalDate birthday) {
        return userMapper.updateExtras(username, gender, genderCustom, birthday) > 0;
    }

    /**
     * 更新会话 token。
     * @param username 用户名。
     * @param sessionToken token；null 表示清空。
     * @return 是否成功。
     */
    public boolean updateSessionToken(String username, String sessionToken) {
        return userMapper.updateSessionToken(username, sessionToken) > 0;
    }

    /**
     * 更新角色。
     * @param username 用户名。
     * @param role 新角色。
     * @return 是否成功。
     */
    public boolean updateRole(String username, String role) {
        return userMapper.updateRole(username, role) > 0;
    }

    /**
     * 更新启用状态。
     * @param username 用户名。
     * @param enabled 启用时为 true。
     * @return 是否成功。
     */
    public boolean updateEnabled(String username, boolean enabled) {
        return userMapper.updateEnabled(username, enabled) > 0;
    }

    /**
     * 更新邮箱。
     * @param username 用户名。
     * @param email 新邮箱。
     * @return 是否成功。
     */
    public boolean updateEmail(String username, String email) {
        return userMapper.updateEmail(username, email) > 0;
    }
}
