package com.pyisland.server.weather.controller;

import com.pyisland.server.weather.service.QWeatherService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 用户侧天气接口（仅 Pro 用户可用）。
 */
@RestController
@RequestMapping("/v1/user/weather")
@PreAuthorize("hasRole('PRO')")
public class UserWeatherController {

    private final QWeatherService qWeatherService;

    public UserWeatherController(QWeatherService qWeatherService) {
        this.qWeatherService = qWeatherService;
    }

    /**
     * 获取和风天气 3 天预报。
     */
    @GetMapping("/daily-3d")
    public ResponseEntity<?> getThreeDayForecast(@RequestParam("location") String location,
                                                 @RequestParam(value = "lang", defaultValue = "zh") String lang,
                                                 @RequestParam(value = "unit", defaultValue = "m") String unit) {
        try {
            Map<String, Object> data = qWeatherService.getThreeDayForecast(location, lang, unit);
            return ResponseEntity.ok(Map.of("code", 200, "message", "success", "data", data));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(503).body(Map.of("code", 503, "message", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(502).body(Map.of("code", 502, "message", "天气服务请求失败"));
        }
    }

    /**
     * 获取当前位置天气预警。
     */
    @GetMapping("/alerts")
    public ResponseEntity<?> getCurrentAlerts(@RequestParam("location") String location,
                                              @RequestParam(value = "lang", defaultValue = "zh") String lang) {
        try {
            Map<String, Object> data = qWeatherService.getCurrentAlerts(location, lang);
            return ResponseEntity.ok(Map.of("code", 200, "message", "success", "data", data));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(503).body(Map.of("code", 503, "message", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(502).body(Map.of("code", 502, "message", "天气预警服务请求失败"));
        }
    }
}
