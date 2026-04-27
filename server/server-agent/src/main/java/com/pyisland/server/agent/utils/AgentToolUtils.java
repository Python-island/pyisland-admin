package com.pyisland.server.agent.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pyisland.server.agent.service.AgentToolExecutionService;
import com.pyisland.server.weather.service.QWeatherService;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 工具实现集合。
 */
public class AgentToolUtils {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final String UAPIS_IPINFO_URL_TEMPLATE = "https://uapis.cn/api/v1/network/ipinfo?ip=%s";

    private final QWeatherService qWeatherService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AgentToolUtils(QWeatherService qWeatherService) {
        this.qWeatherService = qWeatherService;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public AgentToolExecutionService.ToolResult getUserIp(AgentToolExecutionService.ExecutionContext context) {
        String ip = context == null ? "" : AgentIpUtils.sanitizeIp(context.clientIp());
        if (ip.isBlank()) {
            return AgentToolExecutionService.ToolResult.error("user.ip.get", "client ip is unavailable");
        }
        return AgentToolExecutionService.ToolResult.ok("user.ip.get", Map.of(
                "ip", ip,
                "provider", "request-context"
        ));
    }

    public AgentToolExecutionService.ToolResult resolveLocationByIp(Map<String, Object> arguments,
                                                                    AgentToolExecutionService.ExecutionContext context) {
        String ip = AgentIpUtils.sanitizeIp(arg(arguments, "ip"));
        if (ip.isBlank() && context != null) {
            ip = AgentIpUtils.sanitizeIp(context.clientIp());
        }
        if (ip.isBlank()) {
            return AgentToolExecutionService.ToolResult.error("location.by_ip.resolve", "ip is required");
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
                return AgentToolExecutionService.ToolResult.error("location.by_ip.resolve", "lat/lon resolved from ip is empty");
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
            return AgentToolExecutionService.ToolResult.ok("location.by_ip.resolve", result);
        } catch (Exception exception) {
            return AgentToolExecutionService.ToolResult.error("location.by_ip.resolve", AgentStringUtils.trimToEmpty(exception.getMessage()));
        }
    }

    public AgentToolExecutionService.ToolResult queryWeather(Map<String, Object> arguments) {
        String location = arg(arguments, "location");
        if (location.isBlank()) {
            return AgentToolExecutionService.ToolResult.error("weather.query", "location is required");
        }
        try {
            Map<String, Object> daily = qWeatherService.getThreeDayForecast(location, "zh", "m");
            Map<String, Object> alerts = qWeatherService.getCurrentAlerts(location, "zh");
            return AgentToolExecutionService.ToolResult.ok("weather.query", Map.of(
                    "location", location,
                    "daily3d", daily,
                    "alerts", alerts
            ));
        } catch (Exception exception) {
            return AgentToolExecutionService.ToolResult.error("weather.query", AgentStringUtils.trimToEmpty(exception.getMessage()));
        }
    }

    public AgentToolExecutionService.ToolResult getCurrentTime() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        return AgentToolExecutionService.ToolResult.ok("time.now", Map.of(
                "iso", now.toOffsetDateTime().toString(),
                "epochMillis", now.toInstant().toEpochMilli(),
                "timezone", now.getZone().getId(),
                "date", now.toLocalDate().toString(),
                "time", now.toLocalTime().withNano(0).toString()
        ));
    }

    public AgentToolExecutionService.ToolResult getSessionContext(AgentToolExecutionService.ExecutionContext context) {
        String username = context == null ? "" : AgentStringUtils.trimToEmpty(context.username());
        String clientIp = context == null ? "" : AgentIpUtils.sanitizeIp(context.clientIp());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("username", username);
        result.put("clientIp", clientIp);
        result.put("timestamp", Instant.now().toString());
        return AgentToolExecutionService.ToolResult.ok("session.context.get", result);
    }

    public AgentToolExecutionService.ToolResult lookupWeatherCity(Map<String, Object> arguments) {
        String query = arg(arguments, "query");
        if (query.isBlank()) {
            query = arg(arguments, "city");
        }
        if (query.isBlank()) {
            return AgentToolExecutionService.ToolResult.error("weather.city.lookup", "query is required");
        }
        String lang = arg(arguments, "lang");
        if (lang.isBlank()) {
            lang = "zh";
        }
        try {
            Map<String, Object> geo = qWeatherService.lookupCity(query, lang);
            String location = extractFirstLocationId(geo);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("query", query);
            result.put("lang", lang);
            result.put("resolvedLocation", location);
            result.put("geo", geo);
            return AgentToolExecutionService.ToolResult.ok("weather.city.lookup", result);
        } catch (Exception exception) {
            return AgentToolExecutionService.ToolResult.error("weather.city.lookup", AgentStringUtils.trimToEmpty(exception.getMessage()));
        }
    }

    public AgentToolExecutionService.ToolResult queryWeatherByCity(Map<String, Object> arguments) {
        String query = arg(arguments, "query");
        if (query.isBlank()) {
            query = arg(arguments, "city");
        }
        if (query.isBlank()) {
            return AgentToolExecutionService.ToolResult.error("weather.by_city.query", "query is required");
        }
        String lang = arg(arguments, "lang");
        if (lang.isBlank()) {
            lang = "zh";
        }
        try {
            Map<String, Object> geo = qWeatherService.lookupCity(query, lang);
            String location = extractFirstLocationId(geo);
            if (location.isBlank()) {
                return AgentToolExecutionService.ToolResult.error("weather.by_city.query", "city location is unavailable");
            }
            Map<String, Object> daily = qWeatherService.getThreeDayForecast(location, lang, "m");
            Map<String, Object> alerts = qWeatherService.getCurrentAlerts(location, lang);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("query", query);
            result.put("lang", lang);
            result.put("location", location);
            result.put("geo", geo);
            result.put("daily3d", daily);
            result.put("alerts", alerts);
            return AgentToolExecutionService.ToolResult.ok("weather.by_city.query", result);
        } catch (Exception exception) {
            return AgentToolExecutionService.ToolResult.error("weather.by_city.query", AgentStringUtils.trimToEmpty(exception.getMessage()));
        }
    }

    public AgentToolExecutionService.ToolResult queryWeatherQuotaStatus() {
        try {
            return AgentToolExecutionService.ToolResult.ok("weather.quota.status", qWeatherService.getMonthlyQuotaStatus());
        } catch (Exception exception) {
            return AgentToolExecutionService.ToolResult.error("weather.quota.status", AgentStringUtils.trimToEmpty(exception.getMessage()));
        }
    }

    private String arg(Map<String, Object> arguments, String key) {
        if (arguments == null || key == null) {
            return "";
        }
        Object value = arguments.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String extractFirstLocationId(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return "";
        }
        Object locations = payload.get("location");
        if (locations instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Map<?, ?> firstMap) {
                return AgentStringUtils.trimToEmpty(AgentStringUtils.toStringValue(firstMap.get("id")));
            }
        }
        return AgentStringUtils.trimToEmpty(AgentStringUtils.toStringValue(payload.get("id")));
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
}
