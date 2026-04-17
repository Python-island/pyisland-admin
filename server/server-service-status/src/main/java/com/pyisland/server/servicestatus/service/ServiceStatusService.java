package com.pyisland.server.servicestatus.service;

import com.pyisland.server.servicestatus.entity.ServiceStatus;
import com.pyisland.server.servicestatus.mapper.ServiceStatusMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 接口状态服务。
 */
@Service
public class ServiceStatusService {

    private final ServiceStatusMapper serviceStatusMapper;

    /**
     * 构造接口状态服务。
     * @param serviceStatusMapper 接口状态数据访问接口。
     */
    public ServiceStatusService(ServiceStatusMapper serviceStatusMapper) {
        this.serviceStatusMapper = serviceStatusMapper;
    }

    /**
     * 按接口名称查询状态。
     * @param apiName 接口名称。
     * @return 接口状态。
     */
    public ServiceStatus getByApiName(String apiName) {
        return serviceStatusMapper.selectByApiName(apiName);
    }

    /**
     * 查询全部接口状态。
     * @return 接口状态列表。
     */
    public List<ServiceStatus> listAll() {
        return serviceStatusMapper.selectAll();
    }

    /**
     * 更新接口状态。
     * @param apiName 接口名称。
     * @param status 接口可用状态。
     * @param message 状态说明。
     * @param remark 备注信息。
     * @return 更新后的状态实体。
     */
    public ServiceStatus updateStatus(String apiName, Boolean status, String message, String remark) {
        ServiceStatus existing = serviceStatusMapper.selectByApiName(apiName);
        if (existing != null) {
            existing.setStatus(status);
            existing.setMessage(message);
            existing.setRemark(remark);
            existing.setUpdatedAt(LocalDateTime.now());
            serviceStatusMapper.update(existing);
            return existing;
        } else {
            ServiceStatus ss = new ServiceStatus();
            ss.setApiName(apiName);
            ss.setStatus(status);
            ss.setMessage(message);
            ss.setRemark(remark);
            ss.setUpdatedAt(LocalDateTime.now());
            serviceStatusMapper.insert(ss);
            return ss;
        }
    }
}
