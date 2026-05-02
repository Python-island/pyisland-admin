package com.pyisland.server.agent.mq;

import com.pyisland.server.agent.config.AgentBillingMqConfig;
import com.pyisland.server.user.mapper.AgentUsageStatsMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Agent 用量统计异步落库消费者。
 * 从 RabbitMQ 消费用量消息，增量写入 MySQL。
 */
@Component
public class AgentUsageStatsConsumer {

    private static final Logger log = LoggerFactory.getLogger(AgentUsageStatsConsumer.class);

    private final AgentUsageStatsMapper usageStatsMapper;

    public AgentUsageStatsConsumer(AgentUsageStatsMapper usageStatsMapper) {
        this.usageStatsMapper = usageStatsMapper;
    }

    @RabbitListener(queues = AgentBillingMqConfig.USAGE_STATS_QUEUE)
    public void onMessage(AgentUsageStatsMessage message) {
        if (message == null || message.modelName() == null || message.modelName().isBlank()) {
            return;
        }
        try {
            usageStatsMapper.upsertDelta(
                    message.modelName().trim(),
                    message.inputTokens(),
                    message.cachedTokens(),
                    message.outputTokens(),
                    message.reasoningTokens(),
                    1,
                    message.costMicroFen(),
                    LocalDateTime.now()
            );
            log.debug("agent usage db-sync: persisted usage for model={}, input={}, cached={}, output={}, cost={}μ¥",
                    message.modelName(), message.inputTokens(), message.cachedTokens(),
                    message.outputTokens(), message.costMicroFen());
        } catch (Exception ex) {
            log.error("agent usage db-sync: failed to persist usage for model={}, err={}",
                    message.modelName(), ex.getMessage(), ex);
        }
    }
}
