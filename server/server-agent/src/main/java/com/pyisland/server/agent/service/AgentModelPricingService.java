package com.pyisland.server.agent.service;

import com.pyisland.server.user.entity.AgentModelPricing;
import com.pyisland.server.user.mapper.AgentModelPricingMapper;
import com.pyisland.server.user.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 模型定价与扣费服务。
 */
@Service
public class AgentModelPricingService {

    private static final Logger log = LoggerFactory.getLogger(AgentModelPricingService.class);

    private final AgentModelPricingMapper pricingMapper;
    private final UserMapper userMapper;

    public AgentModelPricingService(AgentModelPricingMapper pricingMapper, UserMapper userMapper) {
        this.pricingMapper = pricingMapper;
        this.userMapper = userMapper;
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
        return pricingMapper.selectByModelName(modelName.trim());
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
        if (existing != null) {
            return pricingMapper.updateByModelName(name, inputPriceFenPerMillion, outputPriceFenPerMillion, enabled, LocalDateTime.now()) > 0;
        }
        AgentModelPricing pricing = new AgentModelPricing();
        pricing.setModelName(name);
        pricing.setInputPriceFenPerMillion(inputPriceFenPerMillion);
        pricing.setOutputPriceFenPerMillion(outputPriceFenPerMillion);
        pricing.setEnabled(enabled);
        pricing.setUpdatedAt(LocalDateTime.now());
        return pricingMapper.insert(pricing) > 0;
    }

    /**
     * 删除模型定价。
     */
    public boolean delete(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return false;
        }
        return pricingMapper.deleteByModelName(modelName.trim()) > 0;
    }

    /**
     * 根据 token 用量扣费。
     *
     * @param username    用户名。
     * @param modelName   模型名。
     * @param inputTokens  输入 token 数。
     * @param outputTokens 输出 token 数。
     * @return 扣费金额（分），0 表示免费或未配置定价，-1 表示余额不足。
     */
    public long deductForUsage(String username, String modelName, int inputTokens, int outputTokens) {
        if (username == null || username.isBlank() || modelName == null || modelName.isBlank()) {
            return 0;
        }
        AgentModelPricing pricing = pricingMapper.selectByModelName(modelName.trim());
        if (pricing == null || !Boolean.TRUE.equals(pricing.getEnabled())) {
            return 0;
        }
        long inputFen = pricing.getInputPriceFenPerMillion() == null ? 0 : pricing.getInputPriceFenPerMillion();
        long outputFen = pricing.getOutputPriceFenPerMillion() == null ? 0 : pricing.getOutputPriceFenPerMillion();
        // 计算费用：(tokens / 1_000_000) * priceFenPerMillion，向上取整到分
        long costFen = ceilDiv((long) inputTokens * inputFen, 1_000_000L)
                     + ceilDiv((long) outputTokens * outputFen, 1_000_000L);
        if (costFen <= 0) {
            return 0;
        }
        int rows = userMapper.deductBalance(username.trim(), costFen);
        if (rows == 0) {
            log.warn("agent billing: balance insufficient for user={}, model={}, costFen={}", username, modelName, costFen);
            return -1;
        }
        log.info("agent billing: deducted {}fen from user={}, model={}, inputTokens={}, outputTokens={}",
                costFen, username, modelName, inputTokens, outputTokens);
        return costFen;
    }

    private static long ceilDiv(long a, long b) {
        return (a + b - 1) / b;
    }
}
