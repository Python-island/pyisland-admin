package com.pyisland.server.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pyisland.server.weather.service.QWeatherService;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 工具执行器（Phase 4: ReAct）。
 */
@Service
public class AgentToolExecutionService {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final String IP_GEO_URL_TEMPLATE = "http://ip-api.com/json/%s?lang=zh-CN";

    private final QWeatherService qWeatherService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AgentToolExecutionService(QWeatherService qWeatherService) {
        this.qWeatherService = qWeatherService;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public ToolResult execute(String toolName,
                              Map<String, Object> arguments,
                              boolean proUser,
                              ExecutionContext context) {
        String safeToolName = normalize(toolName);
        if (safeToolName.isBlank()) {
            return ToolResult.error("unknown", "tool name is empty");
        }
        if ("user.ip.get".equals(safeToolName)) {
            return getUserIp(context);
        }
        if ("location.by_ip.resolve".equals(safeToolName)) {
            return resolveLocationByIp(arguments, context);
        }
        if ("weather.query".equals(safeToolName)) {
            if (!proUser) {
                return ToolResult.error("weather.query", "weather tool requires pro user");
            }
            return queryWeather(arguments);
        }
        return ToolResult.error(safeToolName, "tool not supported");
    }

    private ToolResult getUserIp(ExecutionContext context) {
        String ip = context == null ? "" : normalize(context.clientIp());
        if (ip.isBlank()) {
            return ToolResult.error("user.ip.get", "client ip is unavailable");
        }
        return ToolResult.ok("user.ip.get", Map.of(
                "ip", ip
        ));
    }

    private ToolResult resolveLocationByIp(Map<String, Object> arguments, ExecutionContext context) {
        String ip = normalize(arg(arguments, "ip"));
        if (ip.isBlank() && context != null) {
            ip = normalize(context.clientIp());
        }
        if (ip.isBlank()) {
            return ToolResult.error("location.by_ip.resolve", "ip is required");
        }
        try {
            Map<String, Object> ipGeo = requestIpLocation(ip);
            String city = normalize(value(ipGeo.get("city")));
            if (city.isBlank()) {
                return ToolResult.error("location.by_ip.resolve", "city resolved from ip is empty");
            }
            Map<String, Object> cityPayload = qWeatherService.lookupCity(city, "zh");
            Map<String, Object> locationItem = firstLocation(cityPayload);
            String locationId = normalize(value(locationItem.get("id")));
            if (locationId.isBlank()) {
                return ToolResult.error("location.by_ip.resolve", "qweather location id is empty");
            }
            return ToolResult.ok("location.by_ip.resolve", Map.of(
                    "ip", ip,
                    "city", city,
                    "location", locationId,
                    "provider", "ip-api+qweather"
            ));
        } catch (Exception exception) {
            return ToolResult.error("location.by_ip.resolve", normalize(exception.getMessage()));
        }
    }

    private Map<String, Object> requestIpLocation(String ip) throws Exception {
        String safeIp = URLEncoder.encode(ip, StandardCharsets.UTF_8);
        String url = String.format(IP_GEO_URL_TEMPLATE, safeIp);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(6))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("ip geo request failed: HTTP " + response.statusCode());
        }
        String body = response.body() == null ? "" : response.body().trim();
        if (body.isBlank()) {
            throw new IllegalStateException("ip geo response is empty");
        }
        Map<String, Object> payload = objectMapper.readValue(body, MAP_TYPE);
        String status = normalize(value(payload.get("status"))).toLowerCase();
        if (!status.isBlank() && !"success".equals(status)) {
            throw new IllegalStateException("ip geo resolve failed: " + normalize(value(payload.get("message"))));
        }
        return payload;
    }

    private Map<String, Object> firstLocation(Map<String, Object> payload) {
        if (payload == null) {
            return Map.of();
        }
        Object raw = payload.get("location");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return Map.of();
        }
        Object first = list.get(0);
        if (!(first instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = entry.getKey() == null ? "" : String.valueOf(entry.getKey());
            result.put(key, entry.getValue());
        }
        return result;
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private ToolResult queryWeather(Map<String, Object> arguments) {
        String location = arg(arguments, "location");
        if (location.isBlank()) {
            return ToolResult.error("weather.query", "location is required");
        }
        try {
            Map<String, Object> daily = qWeatherService.getThreeDayForecast(location, "zh", "m");
            Map<String, Object> alerts = qWeatherService.getCurrentAlerts(location, "zh");
            return ToolResult.ok("weather.query", Map.of(
                    "location", location,
                    "daily3d", daily,
                    "alerts", alerts
            ));
        } catch (Exception exception) {
            return ToolResult.error("weather.query", normalize(exception.getMessage()));
        }
    }

    private String arg(Map<String, Object> arguments, String key) {
        if (arguments == null || key == null) {
            return "";
        }
        Object value = arguments.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    public record ExecutionContext(String username, String clientIp) {
    }

    public record ToolResult(String tool, boolean success, Object data, String error) {

        public static ToolResult ok(String tool, Object data) {
            return new ToolResult(tool, true, data, "");
        }

        public static ToolResult error(String tool, String error) {
            return new ToolResult(tool, false, Map.of(), error == null ? "" : error.trim());
        }
    }
}
