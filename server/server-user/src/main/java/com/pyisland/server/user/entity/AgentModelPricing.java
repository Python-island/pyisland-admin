package com.pyisland.server.user.entity;

import java.time.LocalDateTime;

/**
 * Agent 模型定价实体。
 * 对应表 agent_model_pricing。
 */
public class AgentModelPricing {

    private Long id;
    private String modelName;
    private Long inputPriceFenPerMillion;
    private Long outputPriceFenPerMillion;
    private Boolean enabled;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public Long getInputPriceFenPerMillion() {
        return inputPriceFenPerMillion;
    }

    public void setInputPriceFenPerMillion(Long inputPriceFenPerMillion) {
        this.inputPriceFenPerMillion = inputPriceFenPerMillion;
    }

    public Long getOutputPriceFenPerMillion() {
        return outputPriceFenPerMillion;
    }

    public void setOutputPriceFenPerMillion(Long outputPriceFenPerMillion) {
        this.outputPriceFenPerMillion = outputPriceFenPerMillion;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
