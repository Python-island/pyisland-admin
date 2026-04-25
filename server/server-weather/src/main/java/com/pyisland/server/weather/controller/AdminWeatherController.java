package com.pyisland.server.weather.controller;

import com.pyisland.server.weather.service.QWeatherService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端天气接口。
 */
@RestController
@RequestMapping("/v1/admin/weather")
@PreAuthorize("hasRole('ADMIN')")
public class AdminWeatherController {

    private final QWeatherService qWeatherService;

    public AdminWeatherController(QWeatherService qWeatherService) {
        this.qWeatherService = qWeatherService;
    }

    /**
     * 查询和风天气本月调用配额使用情况。
     */
    @GetMapping("/quota")
    public ResponseEntity<?> getProviderQuotaStatus() {
        Map<String, Object> data = qWeatherService.getMonthlyQuotaStatus();
        return ResponseEntity.ok(Map.of("code", 200, "message", "success", "data", data));
    }
}
