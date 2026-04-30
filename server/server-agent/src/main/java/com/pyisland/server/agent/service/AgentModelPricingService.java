package com.pyisland.server.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pyisland.server.user.entity.AgentModelPricing;
import com.pyisland.server.user.mapper.AgentModelPricingMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 模型定价与扣费服务。
 */
@Service
public class AgentModelPricingService {

    private static final Logger log = LoggerFactory.getLogger(AgentModelPricingService.class);
    private static final String CACHE_KEY_PREFIX = "agent:pricing:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        OBJECT_MAPPER.findAndRegisterModules();
    }

    private final AgentModelPricingMapper pricingMapper;
    private final AgentBalanceRedisService balanceRedisService;
    private final StringRedisTemplate pricingRedisTemplate;

    public AgentModelPricingService(AgentModelPricingMapper pricingMapper,
                                    AgentBalanceRedisService balanceRedisService,
                                    @Qualifier("agentPricingRedisTemplate") StringRedisTemplate pricingRedisTemplate) {
        this.pricingMapper = pricingMapper;
        this.balanceRedisService = balanceRedisService;
        this.pricingRedisTemplate = pricingRedisTemplate;
    }

    /**
     * 查询全部模型定价。
     */
    public List<AgentModelPricing> listAll() {
        return pricingMapper.selectAll();
    }

    /**
     * 按模型名查询定价。
     */
    public AgentModelPricing getByModelName(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return null;
        }
        return getCachedPricing(modelName.trim());
    }

    /**
     * 新增或更新模型定价。
     */
    public boolean upsert(String modelName, long inputPriceFenPerMillion, long outputPriceFenPerMillion, boolean enabled) {
        if (modelName == null || modelName.isBlank()) {
            return false;
        }
        String name = modelName.trim();
        AgentModelPricing existing = pricingMapper.selectByModelName(name);
        boolean ok;
        if (existing != null) {
            ok = pricingMapper.updateByModelName(name, inputPriceFenPerMillion, outputPriceFenPerMillion, enabled, LocalDateTime.now()) > 0;
        } else {
            AgentModelPricing pricing = new AgentModelPricing();
            pricing.setModelName(name);
            pricing.setInputPriceFenPerMillion(inputPriceFenPerMillion);
            pricing.setOutputPriceFenPerMillion(outputPriceFenPerMillion);
            pricing.setEnabled(enabled);
            pricing.setUpdatedAt(LocalDateTime.now());
            ok = pricingMapper.insert(pricing) > 0;
        }
        if (ok) {
            evictCache(name);
        }
        return ok;
    }

    /**
     * 删除模型定价。
     */
    public boolean delete(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return false;
        }
        String name = modelName.trim();
        boolean ok = pricingMapper.deleteByModelName(name) > 0;
        if (ok) {
            evictCache(name);
        }
        return ok;
    }

    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000L);
    private static final int SCALE = 8;

    /**
     * 根据 token 用量扣费，精度 8 位小数。
     *
     * @param username    用户名。
     * @param modelName   模型名。
     * @param inputTokens  输入 token 数。
     * @param outputTokens 输出 token 数。
     * @return 扣费金额（分，8位小数），BigDecimal.ZERO 表示免费或未配置定价，负数表示余额不足。
     */
    public BigDecimal deductForUsage(String username, String modelName, int inputTokens, int outputTokens) {
        if (username == null || username.isBlank() || modelName == null || modelName.isBlank()) {
            return BigDecimal.ZERO;
        }
        AgentModelPricing pricing = getCachedPricing(modelName.trim());
        if (pricing == null || !Boolean.TRUE.equals(pricing.getEnabled())) {
            return BigDecimal.ZERO;
        }
        long inputFen = pricing.getInputPriceFenPerMillion() == null ? 0 : pricing.getInputPriceFenPerMillion();
        long outputFen = pricing.getOutputPriceFenPerMillion() == null ? 0 : pricing.getOutputPriceFenPerMillion();
        // costFen = (inputTokens * inputPriceFen / 1_000_000) + (outputTokens * outputPriceFen / 1_000_000)
        BigDecimal inputCost = BigDecimal.valueOf(inputTokens)
                .multiply(BigDecimal.valueOf(inputFen))
                .divide(ONE_MILLION, SCALE, RoundingMode.HALF_UP);
        BigDecimal outputCost = BigDecimal.valueOf(outputTokens)
                .multiply(BigDecimal.valueOf(outputFen))
                .divide(ONE_MILLION, SCALE, RoundingMode.HALF_UP);
        BigDecimal costFen = inputCost.add(outputCost);
        if (costFen.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal newBalance = balanceRedisService.deduct(username.trim(), costFen, modelName.trim(), inputTokens, outputTokens);
        if (newBalance == null) {
            log.warn("agent billing: balance insufficient for user={}, model={}, costFen={}", username, modelName, costFen);
            return BigDecimal.valueOf(-1);
        }
        log.info("agent billing: deducted {} fen from user={}, model={}, inputTokens={}, outputTokens={}",
                costFen.toPlainString(), username, modelName, inputTokens, outputTokens);
        return costFen;
    }

    /**
     * 从 Redis 缓存获取定价，未命中则从 DB 加载并写入缓存。
     */
    private AgentModelPricing getCachedPricing(String modelName) {
        String key = CACHE_KEY_PREFIX + modelName;
        try {
            String json = pricingRedisTemplate.opsForValue().get(key);
            if (json != null) {
                return OBJECT_MAPPER.readValue(json, AgentModelPricing.class);
            }
        } catch (Exception e) {
            log.warn("agent pricing cache: read failed, key={}, err={}", key, e.getMessage());
        }
        AgentModelPricing pricing = pricingMapper.selectByModelName(modelName);
        if (pricing != null) {
            try {
                String json = OBJECT_MAPPER.writeValueAsString(pricing);
                pricingRedisTemplate.opsForValue().set(key, json, CACHE_TTL);
            } catch (JsonProcessingException e) {
                log.warn("agent pricing cache: write failed, key={}, err={}", key, e.getMessage());
            }
        }
        return pricing;
    }

    private void evictCache(String modelName) {
        try {
            pricingRedisTemplate.delete(CACHE_KEY_PREFIX + modelName);
        } catch (Exception e) {
            log.warn("agent pricing cache: evict failed, model={}, err={}", modelName, e.getMessage());
        }
    }
}
