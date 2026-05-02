package com.pyisland.server.agent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * Agent 用量统计 Redis 服务（DB13）。
 * <p>
 * 使用 Redis Hash 按模型名聚合统计，每次计费后原子增量写入。
 * key 格式：agent:usage:{modelName}
 * hash fields：inputTokens, cachedTokens, outputTokens, reasoningTokens, requestCount, costMicroFen
 */
@Service
public class AgentUsageStatsRedisService {

    private static final Logger log = LoggerFactory.getLogger(AgentUsageStatsRedisService.class);
    private static final String KEY_PREFIX = "agent:usage:";
    /** 存储所有已记录模型名的 set key */
    private static final String MODELS_SET_KEY = "agent:usage:__models__";

    private final StringRedisTemplate redisTemplate;

    public AgentUsageStatsRedisService(
            @Qualifier("agentUsageRedisTemplate") StringRedisTemplate redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 原子增量记录一次请求的用量。
     *
     * @param modelName       模型名。
     * @param inputTokens     输入 token 数。
     * @param cachedTokens    缓存命中 token 数。
     * @param outputTokens    输出 token 数。
     * @param reasoningTokens 推理 token 数。
     * @param costFen         本次费用（分，8 位小数）。
     */
    public void recordUsage(String modelName, int inputTokens, int cachedTokens,
                            int outputTokens, int reasoningTokens, BigDecimal costFen) {
        if (modelName == null || modelName.isBlank()) return;
        String key = KEY_PREFIX + modelName.trim();
        // costFen 转为 microFen（乘以 10^8）以 long 存储
        long costMicroFen = costFen == null ? 0L : costFen.movePointRight(8).longValue();
        try {
            redisTemplate.opsForHash().increment(key, "inputTokens", inputTokens);
            redisTemplate.opsForHash().increment(key, "cachedTokens", cachedTokens);
            redisTemplate.opsForHash().increment(key, "outputTokens", outputTokens);
            redisTemplate.opsForHash().increment(key, "reasoningTokens", reasoningTokens);
            redisTemplate.opsForHash().increment(key, "requestCount", 1);
            redisTemplate.opsForHash().increment(key, "costMicroFen", costMicroFen);
            // 记录模型名到 set 中
            redisTemplate.opsForSet().add(MODELS_SET_KEY, modelName.trim());
        } catch (Exception e) {
            log.error("agent usage redis: failed to record usage for model={}, err={}", modelName, e.getMessage());
        }
    }

    /**
     * 读取所有模型的实时用量统计。
     *
     * @return 模型名 → 用量 map 列表。
     */
    public List<Map<String, Object>> getAllModelStats() {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            Set<String> models = redisTemplate.opsForSet().members(MODELS_SET_KEY);
            if (models == null || models.isEmpty()) return result;
            for (String model : models) {
                String key = KEY_PREFIX + model;
                Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
                if (entries.isEmpty()) continue;
                Map<String, Object> stat = new LinkedHashMap<>();
                stat.put("modelName", model);
                stat.put("totalInputTokens", parseLong(entries.get("inputTokens")));
                stat.put("totalCachedTokens", parseLong(entries.get("cachedTokens")));
                stat.put("totalOutputTokens", parseLong(entries.get("outputTokens")));
                stat.put("totalReasoningTokens", parseLong(entries.get("reasoningTokens")));
                stat.put("totalRequestCount", parseLong(entries.get("requestCount")));
                long microFen = parseLong(entries.get("costMicroFen"));
                // microFen → fen（8位小数）
                stat.put("totalCostFen", BigDecimal.valueOf(microFen).movePointLeft(8).toPlainString());
                result.add(stat);
            }
        } catch (Exception e) {
            log.error("agent usage redis: failed to read stats, err={}", e.getMessage());
        }
        return result;
    }

    private static long parseLong(Object val) {
        if (val == null) return 0;
        try {
            return Long.parseLong(val.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
