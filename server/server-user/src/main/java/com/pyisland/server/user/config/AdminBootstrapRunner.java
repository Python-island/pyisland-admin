package com.pyisland.server.user.config;

import com.pyisland.server.user.entity.User;
import com.pyisland.server.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 首任管理员引导：应用启动完成后执行。
 * 当 user_account 中不存在任何管理员、且配置了
 * {@code admin.bootstrap.username} / {@code admin.bootstrap.password} 时，
 * 自动插入第一个管理员账号。
 */
@Component
public class AdminBootstrapRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UserService userService;
    private final String bootstrapUsername;
    private final String bootstrapEmail;
    private final String bootstrapPassword;

    /**
     * 构造引导器。
     * @param userService 用户服务。
     * @param bootstrapUsername 引导用户名。
     * @param bootstrapEmail 引导邮箱（可为空，自动填充）。
     * @param bootstrapPassword 引导明文密码。
     */
    public AdminBootstrapRunner(UserService userService,
                                @Value("${admin.bootstrap.username:}") String bootstrapUsername,
                                @Value("${admin.bootstrap.email:}") String bootstrapEmail,
                                @Value("${admin.bootstrap.password:}") String bootstrapPassword) {
        this.userService = userService;
        this.bootstrapUsername = bootstrapUsername;
        this.bootstrapEmail = bootstrapEmail;
        this.bootstrapPassword = bootstrapPassword;
    }

    /**
     * 应用就绪后执行。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (bootstrapUsername == null || bootstrapUsername.isBlank()
                || bootstrapPassword == null || bootstrapPassword.isBlank()) {
            return;
        }
        int adminCount = userService.countByRole(User.ROLE_ADMIN);
        if (adminCount > 0) {
            return;
        }
        String email = (bootstrapEmail == null || bootstrapEmail.isBlank())
                ? bootstrapUsername + "@admin.local"
                : bootstrapEmail.trim().toLowerCase();
        User created = userService.register(bootstrapUsername, email, bootstrapPassword, User.ROLE_ADMIN);
        if (created != null) {
            log.warn("[admin-bootstrap] 已创建首任管理员 username={} email={}，请登录后尽快修改密码。",
                    created.getUsername(), created.getEmail());
        } else {
            log.warn("[admin-bootstrap] 首任管理员创建失败（username 或 email 冲突）：username={}", bootstrapUsername);
        }
    }
}
