package com.pyisland.server.agent.controller;

import com.pyisland.server.agent.service.AgentModelPricingService;
import com.pyisland.server.servicestatus.entity.ServiceStatus;
import com.pyisland.server.servicestatus.service.ServiceStatusService;
import com.pyisland.server.user.entity.AgentBillingDlqLog;
import com.pyisland.server.user.entity.AgentModelPricing;
import com.pyisland.server.user.mapper.AgentBillingDlqLogMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 管理端 Agent 配置接口。
 */
@RestController
@RequestMapping("/v1/admin/agent")
@PreAuthorize("hasRole('ADMIN')")
public class AgentAdminController {

    /** service_status 表中 Agent 服务开关对应的 apiName 常量。 */
    public static final String AGENT_SERVICE_API_NAME = "agent-service";

    private final AgentModelPricingService pricingService;
    private final AgentBillingDlqLogMapper dlqLogMapper;
    private final ServiceStatusService serviceStatusService;

    public AgentAdminController(AgentModelPricingService pricingService,
                                AgentBillingDlqLogMapper dlqLogMapper,
                                ServiceStatusService serviceStatusService) {
        this.pricingService = pricingService;
        this.dlqLogMapper = dlqLogMapper;
        this.serviceStatusService = serviceStatusService;
    }

    /**
     * 查询全部模型定价。
     */
    @GetMapping("/model-pricing")
    public ResponseEntity<?> listModelPricing() {
        List<AgentModelPricing> list = pricingService.listAll();
        return ResponseEntity.ok(Map.of("code", 200, "message", "ok", "data", list));
    }

    /**
     * 新增或更新模型定价。
     */
    @PutMapping("/model-pricing")
    public ResponseEntity<?> upsertModelPricing(@RequestBody ModelPricingRequest request) {
        if (request == null || request.modelName() == null || request.modelName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "模型名不能为空"));
        }
        long inputPrice = request.inputPriceFenPerMillion() == null ? 0 : request.inputPriceFenPerMillion();
        long outputPrice = request.outputPriceFenPerMillion() == null ? 0 : request.outputPriceFenPerMillion();
        boolean enabled = request.enabled() == null || request.enabled();
        boolean ok = pricingService.upsert(request.modelName(), inputPrice, outputPrice, enabled);
        if (!ok) {
            return ResponseEntity.internalServerError().body(Map.of("code", 500, "message", "保存失败"));
        }
        return ResponseEntity.ok(Map.of("code", 200, "message", "保存成功"));
    }

    /**
     * 删除模型定价。
     */
    @DeleteMapping("/model-pricing")
    public ResponseEntity<?> deleteModelPricing(@RequestParam String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "模型名不能为空"));
        }
        boolean ok = pricingService.delete(modelName);
        if (!ok) {
            return ResponseEntity.ok(Map.of("code", 404, "message", "未找到该模型定价"));
        }
        return ResponseEntity.ok(Map.of("code", 200, "message", "删除成功"));
    }

    // ========== Agent 服务开关 ==========

    /**
     * 查询 Agent 服务是否开启。
     */
    @GetMapping("/service-enabled")
    public ResponseEntity<?> getServiceEnabled() {
        ServiceStatus ss = serviceStatusService.getByApiName(AGENT_SERVICE_API_NAME);
        boolean enabled = ss == null || Boolean.TRUE.equals(ss.getStatus());
        String message = ss != null ? ss.getMessage() : null;
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", "ok",
                "data", Map.of("enabled", enabled, "statusMessage", message != null ? message : "")
        ));
    }

    /**
     * 设置 Agent 服务开关。
     */
    @PutMapping("/service-enabled")
    public ResponseEntity<?> setServiceEnabled(@RequestBody ServiceEnabledRequest request) {
        boolean enabled = request == null || request.enabled() == null || request.enabled();
        String msg = request != null && request.message() != null ? request.message().trim() : "";
        serviceStatusService.updateStatus(AGENT_SERVICE_API_NAME, enabled, msg, "");
        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", enabled ? "Agent 服务已开启" : "Agent 服务已关闭"
        ));
    }

    private record ServiceEnabledRequest(Boolean enabled, String message) {}

    // ========== DLQ 异常记录 ==========

    /**
     * 查询 DLQ 异常记录。
     */
    @GetMapping("/billing-dlq")
    public ResponseEntity<?> listBillingDlq(@RequestParam(required = false) String status) {
        List<AgentBillingDlqLog> list;
        if (status != null && !status.isBlank()) {
            list = dlqLogMapper.selectByStatus(status.trim());
        } else {
            list = dlqLogMapper.selectAll();
        }
        int pendingCount = dlqLogMapper.countPending();
        return ResponseEntity.ok(Map.of("code", 200, "message", "ok", "data", list, "pendingCount", pendingCount));
    }

    /**
     * 处理 DLQ 记录（标记为 resolved / ignored）。
     */
    @PutMapping("/billing-dlq/{id}/resolve")
    public ResponseEntity<?> resolveBillingDlq(@PathVariable Long id,
                                               @RequestBody DlqResolveRequest request) {
        if (id == null) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "ID 不能为空"));
        }
        String resolveStatus = request == null || request.status() == null ? "resolved" : request.status().trim();
        if (!"resolved".equals(resolveStatus) && !"ignored".equals(resolveStatus)) {
            resolveStatus = "resolved";
        }
        String adminName = resolveAdminUsername();
        int rows = dlqLogMapper.updateStatus(id, resolveStatus, adminName, LocalDateTime.now());
        if (rows == 0) {
            return ResponseEntity.ok(Map.of("code", 404, "message", "未找到该记录"));
        }
        return ResponseEntity.ok(Map.of("code", 200, "message", "处理成功"));
    }

    /**
     * 查询待处理 DLQ 数量。
     */
    @GetMapping("/billing-dlq/pending-count")
    public ResponseEntity<?> billingDlqPendingCount() {
        int count = dlqLogMapper.countPending();
        return ResponseEntity.ok(Map.of("code", 200, "message", "ok", "data", count));
    }

    private String resolveAdminUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }

    private record ModelPricingRequest(
            String modelName,
            Long inputPriceFenPerMillion,
            Long outputPriceFenPerMillion,
            Boolean enabled
    ) {}

    private record DlqResolveRequest(
            String status
    ) {}
}
