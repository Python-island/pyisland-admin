package com.pyisland.server.servicestatus.entity;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 接口状态实体。
 */
public class ServiceStatus implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String apiName;
    private Boolean status;
    private String message;
    private String remark;
    private LocalDateTime updatedAt;

    /**
     * 默认构造函数。
     */
    public ServiceStatus() {
    }

    /**
     * 获取主键 ID。
     * @return 状态 ID。
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置主键 ID。
     * @param id 状态 ID。
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取接口名称。
     * @return 接口名称。
     */
    public String getApiName() {
        return apiName;
    }

    /**
     * 设置接口名称。
     * @param apiName 接口名称。
     */
    public void setApiName(String apiName) {
        this.apiName = apiName;
    }

    /**
     * 获取接口可用状态。
     * @return 是否可用。
     */
    public Boolean getStatus() {
        return status;
    }

    /**
     * 设置接口可用状态。
     * @param status 是否可用。
     */
    public void setStatus(Boolean status) {
        this.status = status;
    }

    /**
     * 获取状态消息。
     * @return 状态消息。
     */
    public String getMessage() {
        return message;
    }

    /**
     * 设置状态消息。
     * @param message 状态消息。
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * 获取备注信息。
     * @return 备注信息。
     */
    public String getRemark() {
        return remark;
    }

    /**
     * 设置备注信息。
     * @param remark 备注信息。
     */
    public void setRemark(String remark) {
        this.remark = remark;
    }

    /**
     * 获取更新时间。
     * @return 更新时间。
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * 设置更新时间。
     * @param updatedAt 更新时间。
     */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
