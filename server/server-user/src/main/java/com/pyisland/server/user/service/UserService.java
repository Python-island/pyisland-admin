package com.pyisland.server.user.service;

import com.pyisland.server.user.entity.UserDailyActiveStat;
import com.pyisland.server.user.entity.User;
import com.pyisland.server.user.mapper.UserMapper;
import com.pyisland.server.user.policy.PasswordHashService;
import com.pyisland.server.user.event.ProBalanceGrantEvent;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 统一用户服务。登录/注册/资料管理/角色调整集中于此。
 */
@Service
public class UserService {

    private static final BigDecimal PRO_AGENT_BONUS_FEN = new BigDecimal("1000");

    private final UserMapper userMapper;
    private final PasswordHashService passwordHashService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 构造用户服务。
     * @param userMapper 用户数据访问。
     * @param passwordHashService 密码哈希服务。
     * @param eventPublisher 事件发布器。
     */
    public UserService(UserMapper userMapper,
                       PasswordHashService passwordHashService,
                       ApplicationEventPublisher eventPublisher) {
        this.userMapper = userMapper;
        this.passwordHashService = passwordHashService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 按用户名校验登录。成功时若仍使用旧 SHA-256 哈希则自动升级为 BCrypt。
     * @param username 用户名。
     * @param password 明文密码。
     * @return 认证成功返回用户实体，否则返回 null。
     */
    public User authenticateByUsername(String username, String password) {
        String normalizedUsername = username == null ? "" : username.trim();
        if (normalizedUsername.isEmpty()) {
            return null;
        }
        User user = userMapper.selectByUsername(normalizedUsername);
        return authenticateUser(user, password);
    }

    /**
     * 按邮箱校验登录。成功时若仍使用旧 SHA-256 哈希则自动升级为 BCrypt。
     * @param email 邮箱。
     * @param password 明文密码。
     * @return 认证成功返回用户实体，否则返回 null。
     */
    public User authenticateByEmail(String email, String password) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (normalizedEmail.isEmpty()) {
            return null;
        }
        User user = userMapper.selectByEmail(normalizedEmail);
        return authenticateUser(user, password);
    }

    /**
     * 兼容认证入口：先按用户名，再按邮箱。
     * @param account 登录账号（用户名或邮箱）。
     * @param password 明文密码。
     * @return 认证成功返回用户实体，否则返回 null。
     */
    public User authenticate(String account, String password) {
        User byUsername = authenticateByUsername(account, password);
        if (byUsername != null) {
            return byUsername;
        }
        return authenticateByEmail(account, password);
    }

    private User authenticateUser(User user, String password) {
        if (user == null) {
            return null;
        }
        if (!passwordHashService.matches(password, user.getPassword())) {
            return null;
        }
        if (Boolean.FALSE.equals(user.getEnabled())) {
            return null;
        }
        if (User.ROLE_PRO.equals(user.getRole())
                && user.getProExpireAt() != null
                && !user.getProExpireAt().isAfter(LocalDateTime.now())) {
            userMapper.updateRole(user.getUsername(), User.ROLE_USER);
            user.setRole(User.ROLE_USER);
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
     * 按用户名查询头像（Redis DB1 缓存）。
     * @param username 用户名。
     * @return 头像 URL；用户不存在时返回 null。
     */
    @Cacheable(cacheNames = "avatar-data", key = "#username", cacheManager = "avatarCacheManager", unless = "#result == null")
    public String getAvatarByUsername(String username) {
        User user = userMapper.selectByUsername(username);
        return user == null ? null : user.getAvatar();
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
    @CacheEvict(cacheNames = "avatar-data", key = "#username", cacheManager = "avatarCacheManager", condition = "#result")
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
    @CacheEvict(cacheNames = "avatar-data", key = "#username", cacheManager = "avatarCacheManager", condition = "#result")
    public boolean updateProfile(String username, String rawPassword, String avatar) {
        String hashed = passwordHashService.hash(rawPassword);
        return userMapper.updateProfile(username, hashed, avatar) > 0;
    }

    /**
     * 仅更新密码。
     * @param username 用户名。
     * @param rawPassword 明文新密码。
     * @return 是否成功。
     */
    public boolean updatePassword(String username, String rawPassword) {
        String hashed = passwordHashService.hash(rawPassword);
        return userMapper.updatePassword(username, hashed) > 0;
    }

    /**
     * 仅更新头像。
     * @param username 用户名。
     * @param avatar 头像 URL。
     * @return 是否成功。
     */
    @CacheEvict(cacheNames = "avatar-data", key = "#username", cacheManager = "avatarCacheManager", condition = "#result")
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
     * 更新用户 TOTP 种子密文。
     * @param username 用户名。
     * @param totpSecretCiphertext AES-GCM 密文（Base64）。
     * @return 是否成功。
     */
    public boolean updateTotpSecret(String username, String totpSecretCiphertext) {
        return userMapper.updateTotpSecret(username, totpSecretCiphertext, LocalDateTime.now()) > 0;
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
     * 更新 Pro 到期时间。
     * @param username 用户名。
     * @param proExpireAt 到期时间。
     * @return 是否成功。
     */
    public boolean updateProExpireAt(String username, LocalDateTime proExpireAt) {
        return userMapper.updateProExpireAt(username, proExpireAt) > 0;
    }

    /**
     * 为用户发放 1 个月 Pro 权益（按自然月顺延）。
     * @param username 用户名。
     * @return 新到期时间；用户不存在时返回 null。
     */
    @Transactional
    public LocalDateTime grantProOneMonth(String username) {
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime base = user.getProExpireAt() != null && user.getProExpireAt().isAfter(now)
                ? user.getProExpireAt()
                : now;
        LocalDateTime nextExpireAt = base.plusMonths(1);
        userMapper.updateProExpireAt(username, nextExpireAt);
        if (!User.ROLE_ADMIN.equals(user.getRole())) {
            userMapper.updateRole(username, User.ROLE_PRO);
        }
        // Pro 开通自动发放 Agent 额度（10 元 = 1000 分）
        userMapper.addBalance(username, PRO_AGENT_BONUS_FEN);
        eventPublisher.publishEvent(new ProBalanceGrantEvent(this, username));
        return nextExpireAt;
    }

    /**
     * 为用户充值 Agent 余额（充值场景）。
     * @param username 用户名。
     * @param amountFen 充值金额（分）。
     */
    @Transactional
    public void addAgentBalance(String username, java.math.BigDecimal amountFen) {
        if (username == null || username.isBlank() || amountFen == null || amountFen.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return;
        }
        userMapper.addBalance(username, amountFen);
        eventPublisher.publishEvent(new ProBalanceGrantEvent(this, username));
    }

    /**
     * 查询用户 Agent 余额（元）。
     * @param username 用户名。
     * @return 余额（元字符串），用户不存在返回 null。
     */
    public String getAgentBalanceYuan(String username) {
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            return null;
        }
        java.math.BigDecimal balanceFen = user.getBalanceFen() != null ? user.getBalanceFen() : java.math.BigDecimal.ZERO;
        return balanceFen.divide(new java.math.BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * 自动降级已过期 Pro 用户。
     * @param now 当前时间。
     * @return 降级数量。
     */
    public int demoteExpiredProUsers(LocalDateTime now) {
        return userMapper.demoteExpiredProUsers(now == null ? LocalDateTime.now() : now);
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

    /**
     * 记录日活跃（同一用户同一天仅记一次）。
     * @param username 用户名。
     * @param role 角色。
     */
    public void recordDailyActive(String username, String role) {
        if (username == null || username.isBlank()) {
            return;
        }
        String normalizedRole = (role == null || role.isBlank()) ? User.ROLE_USER : role;
        userMapper.insertDailyActive(username.trim(), normalizedRole, LocalDate.now(), LocalDateTime.now());
    }

    /**
     * 统计指定日期日活跃用户数。
     * @param activeDate 统计日期。
     * @param role 角色。
     * @return 日活跃数。
     */
    public long countDailyActive(LocalDate activeDate, String role) {
        LocalDate targetDate = activeDate == null ? LocalDate.now() : activeDate;
        return userMapper.countDailyActive(targetDate, role);
    }

    /**
     * 查询日期区间内的日活跃统计。
     * @param startDate 开始日期（含）。
     * @param endDate 结束日期（含）。
     * @param role 角色。
     * @return 日活跃统计列表。
     */
    public List<UserDailyActiveStat> listDailyActiveRange(LocalDate startDate, LocalDate endDate, String role) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            return List.of();
        }
        return userMapper.selectDailyActiveRange(startDate, endDate, role);
    }
}
