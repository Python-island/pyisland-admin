package com.pyisland.server.user.event;

import org.springframework.context.ApplicationEvent;

/**
 * Pro 开通后发放 Agent 余额事件。
 * 由 UserService 发布，AgentBalanceRedisService 监听以清除 Redis 缓存。
 */
public class ProBalanceGrantEvent extends ApplicationEvent {

    private final String username;

    public ProBalanceGrantEvent(Object source, String username) {
        super(source);
        this.username = username;
    }

    public String getUsername() {
        return username;
    }
}
