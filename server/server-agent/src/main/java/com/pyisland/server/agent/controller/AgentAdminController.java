package com.pyisland.server.agent.controller;

import com.pyisland.server.agent.service.AgentModelPricingService;
import com.pyisland.server.user.entity.AgentModelPricing;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理端 Agent 配置接口。
 */
@RestController
@RequestMapping("/v1/admin/agent")
@PreAuthorize("hasRole('ADMIN')")
public class AgentAdminController {

    private final AgentModelPricingService pricingService;

    public AgentAdminController(AgentModelPricingService pricingService) {
        this.pricingService = pricingService;
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

    private record ModelPricingRequest(
            String modelName,
            Long inputPriceFenPerMillion,
            Long outputPriceFenPerMillion,
            Boolean enabled
    ) {}
}
