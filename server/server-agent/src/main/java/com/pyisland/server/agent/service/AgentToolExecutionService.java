package com.pyisland.server.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pyisland.server.agent.utils.AgentIpUtils;
import com.pyisland.server.agent.utils.AgentStringUtils;
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
import java.util.Map;

/**
 * Agent 工具执行器（Phase 4: ReAct）。
 */
@Service
public class AgentToolExecutionService {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final String UAPIS_IPINFO_URL_TEMPLATE = "https://uapis.cn/api/v1/network/ipinfo?ip=%s";

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
        String safeToolName = AgentStringUtils.trimToEmpty(toolName);
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
        String ip = context == null ? "" : AgentIpUtils.sanitizeIp(context.clientIp());
        if (ip.isBlank()) {
            return ToolResult.error("user.ip.get", "client ip is unavailable");
        }
        return ToolResult.ok("user.ip.get", Map.of(
                "ip", ip,
                "provider", "request-context"
        ));
    }

    private ToolResult resolveLocationByIp(Map<String, Object> arguments, ExecutionContext context) {
        String ip = AgentIpUtils.sanitizeIp(arg(arguments, "ip"));
        if (ip.isBlank() && context != null) {
            ip = AgentIpUtils.sanitizeIp(context.clientIp());
        }
        if (ip.isBlank()) {
            return ToolResult.error("location.by_ip.resolve", "ip is required");
        }
        try {
            Map<String, Object> ipGeo = requestIpLocation(ip);
            String resolvedIp = AgentStringUtils.trimToEmpty(AgentStringUtils.toStringValue(ipGeo.get("ip")));
            if (resolvedIp.isBlank()) {
                resolvedIp = ip;
            }
            String region = AgentStringUtils.trimToEmpty(AgentStringUtils.toStringValue(ipGeo.get("region")));
            String[] regionParts = splitRegion(region);
            String country = regionParts[0];
            String regionName = regionParts[1];
            String city = regionParts[2];
            String lat = AgentStringUtils.trimToEmpty(AgentStringUtils.toStringValue(ipGeo.get("latitude")));
            String lon = AgentStringUtils.trimToEmpty(AgentStringUtils.toStringValue(ipGeo.get("longitude")));
            String isp = AgentStringUtils.trimToEmpty(AgentStringUtils.toStringValue(ipGeo.get("isp")));
            String asn = AgentStringUtils.trimToEmpty(AgentStringUtils.toStringValue(ipGeo.get("asn")));
            String llc = AgentStringUtils.trimToEmpty(AgentStringUtils.toStringValue(ipGeo.get("llc")));
            if (lat.isBlank() || lon.isBlank()) {
                return ToolResult.error("location.by_ip.resolve", "lat/lon resolved from ip is empty");
            }
            String location = lon + "," + lat;
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ip", resolvedIp);
            result.put("city", city);
            result.put("regionName", regionName);
            result.put("country", country);
            result.put("latitude", lat);
            result.put("longitude", lon);
            result.put("location", location);
            result.put("region", region);
            result.put("isp", isp);
            result.put("asn", asn);
            result.put("llc", llc);
            result.put("provider", "uapis");
            return ToolResult.ok("location.by_ip.resolve", result);
        } catch (Exception exception) {
            return ToolResult.error("location.by_ip.resolve", AgentStringUtils.trimToEmpty(exception.getMessage()));
        }
    }

    private Map<String, Object> requestIpLocation(String ip) throws Exception {
        String safeIp = AgentStringUtils.trimToEmpty(ip);
        String encodedIp = URLEncoder.encode(safeIp, StandardCharsets.UTF_8);
        String url = String.format(UAPIS_IPINFO_URL_TEMPLATE, encodedIp);
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
        if (payload.containsKey("code") || payload.containsKey("message")) {
            String errorCode = AgentStringUtils.trimToEmpty(AgentStringUtils.toStringValue(payload.get("code")));
            String errorMessage = AgentStringUtils.trimToEmpty(AgentStringUtils.toStringValue(payload.get("message")));
            if (!errorCode.isBlank() || !errorMessage.isBlank()) {
                throw new IllegalStateException("ip geo resolve failed: " + errorCode + (errorMessage.isBlank() ? "" : " - " + errorMessage));
            }
        }
        String latitude = AgentStringUtils.trimToEmpty(AgentStringUtils.toStringValue(payload.get("latitude")));
        String longitude = AgentStringUtils.trimToEmpty(AgentStringUtils.toStringValue(payload.get("longitude")));
        if (latitude.isBlank() || longitude.isBlank()) {
            throw new IllegalStateException("ip geo resolve failed: latitude/longitude missing");
        }
        return payload;
    }

    private String[] splitRegion(String region) {
        String safe = AgentStringUtils.trimToEmpty(region);
        if (safe.isBlank()) {
            return new String[]{"", "", ""};
        }
        String[] parts = safe.split("\\s+");
        if (parts.length == 1) {
            return new String[]{parts[0], "", ""};
        }
        if (parts.length == 2) {
            return new String[]{parts[0], parts[1], ""};
        }
        return new String[]{parts[0], parts[1], parts[2]};
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
            return ToolResult.error("weather.query", AgentStringUtils.trimToEmpty(exception.getMessage()));
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
