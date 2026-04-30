package com.pyisland.server.agent.service;

import com.pyisland.server.agent.config.AgentBillingMqConfig;
import com.pyisland.server.agent.mq.AgentBillingDeductMessage;
import com.pyisland.server.user.entity.User;
import com.pyisland.server.user.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Agent 计费 Redis 余额服务。
 * <p>
 * 使用 Redis DB12 + Lua 脚本实现原子扣减，通过 RabbitMQ 异步落库。
 * Redis key 格式：agent:balance:{username}，值为字符串形式的分（8 位小数）。
 */
@Service
public class AgentBalanceRedisService {

    private static final Logger log = LoggerFactory.getLogger(AgentBalanceRedisService.class);
    private static final String KEY_PREFIX = "agent:balance:";
    private static final int SCALE = 8;

    /**
     * Lua 脚本：原子扣减。
     * KEYS[1] = balance key
     * ARGV[1] = 扣减金额（字符串，8 位小数）
     * 返回值：
     *   "-1"  → key 不存在（需要从 DB 初始化）
     *   "-2"  → 余额不足
     *   其他  → 扣减后的新余额
     */
    private static final String DEDUCT_LUA =
            """
            local bal = redis.call('GET', KEYS[1])
            if bal == false then
                return '-1'
            end
            local current = tonumber(bal)
            local amount  = tonumber(ARGV[1])
            if current < amount then
                return '-2'
            end
            local newBal = current - amount
            local formatted = string.format('%.8f', newBal)
            redis.call('SET', KEYS[1], formatted)
            return formatted
            """;

    private static final DefaultRedisScript<String> DEDUCT_SCRIPT;

    static {
        DEDUCT_SCRIPT = new DefaultRedisScript<>();
        DEDUCT_SCRIPT.setScriptText(DEDUCT_LUA);
        DEDUCT_SCRIPT.setResultType(String.class);
    }

    private final StringRedisTemplate redisTemplate;
    private final UserMapper userMapper;
    private final RabbitTemplate rabbitTemplate;

    public AgentBalanceRedisService(
            @Qualifier("agentBillingRedisTemplate") StringRedisTemplate redisTemplate,
            UserMapper userMapper,
            RabbitTemplate rabbitTemplate
    ) {
        this.redisTemplate = redisTemplate;
        this.userMapper = userMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 原子扣减余额（Redis Lua），成功后通过 RabbitMQ 异步落库。
     *
     * @param username     用户名。
     * @param amountFen    扣减金额（分，8 位小数）。
     * @param modelName    模型名。
     * @param inputTokens  输入 token 数。
     * @param outputTokens 输出 token 数。
     * @return 扣减后的新余额；null 表示余额不足。
     */
    public BigDecimal deduct(String username, BigDecimal amountFen,
                             String modelName, int inputTokens, int outputTokens) {
        String key = KEY_PREFIX + username;
        String amountStr = amountFen.setScale(SCALE, RoundingMode.HALF_UP).toPlainString();

        String result = redisTemplate.execute(DEDUCT_SCRIPT, List.of(key), amountStr);

        if ("-1".equals(result)) {
            // key 不存在，从 DB 加载并重试一次
            if (initFromDb(username)) {
                result = redisTemplate.execute(DEDUCT_SCRIPT, List.of(key), amountStr);
            } else {
                log.warn("agent billing redis: user not found in DB, username={}", username);
                return null;
            }
        }

        if ("-2".equals(result)) {
            log.warn("agent billing redis: balance insufficient, username={}, amount={}", username, amountStr);
            return null;
        }

        BigDecimal newBalance = new BigDecimal(result).setScale(SCALE, RoundingMode.HALF_UP);
        log.info("agent billing redis: deducted {} fen from user={}, newBalance={}", amountStr, username, newBalance.toPlainString());

        // 通过 RabbitMQ 异步落库
        publishDeductMessage(username, amountStr, modelName, inputTokens, outputTokens);

        return newBalance;
    }

    /**
     * 从数据库加载余额到 Redis。
     *
     * @param username 用户名。
     * @return 是否加载成功。
     */
    public boolean initFromDb(String username) {
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            return false;
        }
        BigDecimal balance = user.getBalanceFen() != null ? user.getBalanceFen() : BigDecimal.ZERO;
        String key = KEY_PREFIX + username;
        redisTemplate.opsForValue().set(key, balance.setScale(SCALE, RoundingMode.HALF_UP).toPlainString());
        log.info("agent billing redis: initialized balance for user={}, balance={}", username, balance.toPlainString());
        return true;
    }

    /**
     * 获取 Redis 中的余额（不存在则从 DB 加载）。
     */
    public BigDecimal getBalance(String username) {
        String key = KEY_PREFIX + username;
        String val = redisTemplate.opsForValue().get(key);
        if (val == null) {
            if (initFromDb(username)) {
                val = redisTemplate.opsForValue().get(key);
            }
        }
        return val != null ? new BigDecimal(val).setScale(SCALE, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }

    private void publishDeductMessage(String username, String amountFen,
                                      String modelName, int inputTokens, int outputTokens) {
        try {
            AgentBillingDeductMessage message = new AgentBillingDeductMessage(
                    username, amountFen, modelName, inputTokens, outputTokens);
            rabbitTemplate.convertAndSend(
                    AgentBillingMqConfig.EXCHANGE,
                    AgentBillingMqConfig.ROUTING_KEY,
                    message
            );
            log.info("agent billing mq: published deduct message, username={}, amount={}, model={}",
                    username, amountFen, modelName);
        } catch (Exception e) {
            log.error("agent billing mq: failed to publish deduct message, username={}, amount={}, err={}",
                    username, amountFen, e.getMessage(), e);
        }
    }
}
