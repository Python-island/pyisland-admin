package com.pyisland.server.agent.job;

import com.pyisland.server.user.entity.User;
import com.pyisland.server.user.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Agent 计费对账定时任务。
 * <p>
 * 定期比较 Redis DB12 中的余额与 MySQL user_account.balance_fen，
 * 发现不一致时以 MySQL 为准重置 Redis 余额并记录日志。
 */
@Component
public class AgentBillingReconcileJob {

    private static final Logger log = LoggerFactory.getLogger(AgentBillingReconcileJob.class);
    private static final String KEY_PREFIX = "agent:balance:";
    private static final int SCALE = 8;
    /**
     * 允许的最大偏差（分），超过此阈值才视为不一致。
     * 由于 Redis 扣减先于 MQ 异步落库，短时间内会存在正常偏差。
     */
    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    private final UserMapper userMapper;
    private final StringRedisTemplate billingRedisTemplate;

    public AgentBillingReconcileJob(UserMapper userMapper,
                                     @Qualifier("agentBillingRedisTemplate") StringRedisTemplate billingRedisTemplate) {
        this.userMapper = userMapper;
        this.billingRedisTemplate = billingRedisTemplate;
    }

    /**
     * 每 30 分钟执行一次对账。
     */
    @Scheduled(cron = "0 */30 * * * *")
    public void reconcile() {
        List<User> users = userMapper.selectUsersWithPositiveBalance();
        if (users == null || users.isEmpty()) {
            return;
        }
        int checked = 0;
        int fixed = 0;
        for (User user : users) {
            String username = user.getUsername();
            if (username == null || username.isBlank()) {
                continue;
            }
            String key = KEY_PREFIX + username;
            String redisVal = billingRedisTemplate.opsForValue().get(key);
            if (redisVal == null) {
                // Redis 中没有余额，跳过（下次扣费时会从 DB 加载）
                continue;
            }
            BigDecimal redisBalance;
            try {
                redisBalance = new BigDecimal(redisVal).setScale(SCALE, RoundingMode.HALF_UP);
            } catch (NumberFormatException e) {
                log.warn("agent reconcile: invalid redis value for user={}, value={}", username, redisVal);
                continue;
            }
            BigDecimal dbBalance = user.getBalanceFen() != null
                    ? user.getBalanceFen().setScale(SCALE, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            checked++;

            // Redis 余额应当 <= DB 余额（因为 Redis 先扣减，DB 异步落库有延迟）
            // 如果 Redis > DB，说明 MQ 消息还在途中，属于正常偏差
            // 如果 Redis < DB - tolerance，说明有额外扣减或数据异常
            BigDecimal diff = redisBalance.subtract(dbBalance).abs();
            if (diff.compareTo(TOLERANCE) <= 0) {
                continue;
            }

            // Redis 余额高于 DB 余额：可能是 MQ 落库失败导致 DB 少扣
            // Redis 余额低于 DB 余额：可能是 Redis 被额外扣减或 DB 被手动充值
            if (redisBalance.compareTo(dbBalance) > 0) {
                log.warn("agent reconcile: redis > db, user={}, redis={}, db={}, diff={} (MQ may have failed)",
                        username, redisBalance.toPlainString(), dbBalance.toPlainString(), diff.toPlainString());
                // 不主动修复：可能 MQ 消息仍在队列中，等 DLQ 告警处理
            } else {
                // Redis < DB：以 DB 为准重置 Redis（DB 被充值或 Redis 多扣）
                log.warn("agent reconcile: redis < db, resetting redis, user={}, redis={}, db={}",
                        username, redisBalance.toPlainString(), dbBalance.toPlainString());
                billingRedisTemplate.opsForValue().set(key, dbBalance.toPlainString());
                fixed++;
            }
        }
        if (checked > 0) {
            log.info("agent reconcile: checked={}, fixed={}", checked, fixed);
        }
    }
}
