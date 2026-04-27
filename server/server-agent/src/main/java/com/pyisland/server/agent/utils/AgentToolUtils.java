package com.pyisland.server.agent.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pyisland.server.agent.service.AgentWebAuthorizationService;
import com.pyisland.server.agent.service.AgentToolExecutionService;
import com.pyisland.server.weather.service.QWeatherService;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agent 工具实现集合。
 */
public class AgentToolUtils {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final String UAPIS_IPINFO_URL_TEMPLATE = "https://uapis.cn/api/v1/network/ipinfo?ip=%s";
    private static final String DUCK_DUCK_GO_SEARCH_URL_TEMPLATE = "https://api.duckduckgo.com/?q=%s&format=json&no_redirect=1&no_html=1";
    private static final String DUCK_DUCK_GO_HTML_SEARCH_URL_TEMPLATE = "https://duckduckgo.com/html/?q=%s";
    private static final Pattern DDG_HTML_RESULT_LINK_PATTERN = Pattern.compile("(?is)<a[^>]*class=\"[^\"]*result__a[^\"]*\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>");

    private final QWeatherService qWeatherService;
    private final AgentWebAuthorizationService webAuthorizationService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AgentToolUtils(QWeatherService qWeatherService,
                          AgentWebAuthorizationService webAuthorizationService) {
        this.qWeatherService = qWeatherService;
        this.webAuthorizationService = webAuthorizationService;
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

    private void collectWebSearchResultsFromHtml(String query,
                                                 List<Map<String, Object>> collector,
                                                 int limit) {
        if (collector == null || collector.size() >= limit) {
            return;
        }
        try {
            String encodedQuery = URLEncoder.encode(AgentStringUtils.trimToEmpty(query), StandardCharsets.UTF_8);
            String url = String.format(DUCK_DUCK_GO_HTML_SEARCH_URL_TEMPLATE, encodedQuery);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Accept", "text/html")
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .timeout(Duration.ofSeconds(8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return;
            }
            String html = response.body() == null ? "" : response.body();
            if (html.isBlank()) {
                return;
            }
            Matcher matcher = DDG_HTML_RESULT_LINK_PATTERN.matcher(html);
            while (matcher.find() && collector.size() < limit) {
                String rawHref = AgentStringUtils.trimToEmpty(matcher.group(1));
                String titleHtml = AgentStringUtils.trimToEmpty(matcher.group(2));
                String resolvedUrl = normalizeDuckSearchResultUrl(rawHref);
                if (resolvedUrl.isBlank()) {
                    continue;
                }
                String title = stripHtml(titleHtml);
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("title", title.isBlank() ? resolvedUrl : title);
                entry.put("url", resolvedUrl);
                entry.put("snippet", "");
                collector.add(entry);
            }
        } catch (Exception ignored) {
            // ignore html fallback errors and keep existing collector state
        }
    }

    private String normalizeDuckSearchResultUrl(String rawHref) {
        String href = AgentStringUtils.trimToEmpty(rawHref);
        if (href.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(href);
            String query = AgentStringUtils.trimToEmpty(uri.getQuery());
            if (!query.isBlank()) {
                String[] parts = query.split("&");
                for (String part : parts) {
                    if (part.startsWith("uddg=")) {
                        String encoded = part.substring(5);
                        String decoded = URLDecoder.decode(encoded, StandardCharsets.UTF_8);
                        return normalizeWebUrl(decoded);
                    }
                }
            }
        } catch (Exception ignored) {
            // noop
        }
        return normalizeWebUrl(href);
    }

    private String stripHtml(String html) {
        String source = AgentStringUtils.trimToEmpty(html);
        if (source.isBlank()) {
            return "";
        }
        String noTag = source.replaceAll("(?is)<[^>]+>", " ");
        String decoded = noTag
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
        return decoded.replaceAll("\\s+", " ").trim();
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

    public AgentToolExecutionService.ToolResult searchWeb(Map<String, Object> arguments) {
        String query = arg(arguments, "query");
        if (query.isBlank()) {
            query = arg(arguments, "q");
        }
        if (query.isBlank()) {
            return AgentToolExecutionService.ToolResult.error("web.search", "query is required");
        }
        int limit = 5;
        try {
            String rawLimit = arg(arguments, "limit");
            if (!rawLimit.isBlank()) {
                limit = Math.max(1, Math.min(10, Integer.parseInt(rawLimit)));
            }
        } catch (Exception ignored) {
            limit = 5;
        }
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = String.format(DUCK_DUCK_GO_SEARCH_URL_TEMPLATE, encodedQuery);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Accept", "application/json")
                    .GET()
                    .timeout(Duration.ofSeconds(8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return AgentToolExecutionService.ToolResult.error("web.search", "search request failed: HTTP " + response.statusCode());
            }
            String body = response.body() == null ? "" : response.body().trim();
            if (body.isBlank()) {
                return AgentToolExecutionService.ToolResult.error("web.search", "search response is empty");
            }
            Map<String, Object> payload = objectMapper.readValue(body, MAP_TYPE);
            List<Map<String, Object>> results = new ArrayList<>();
            collectWebSearchResults(payload, results, limit);
            if (results.isEmpty()) {
                collectWebSearchResultsFromHtml(query, results, limit);
            }
            if (results.isEmpty()) {
                return AgentToolExecutionService.ToolResult.error("web.search", "no results found for query: " + query);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("query", query);
            result.put("provider", "duckduckgo");
            result.put("count", results.size());
            result.put("results", results);
            return AgentToolExecutionService.ToolResult.ok("web.search", result);
        } catch (Exception exception) {
            return AgentToolExecutionService.ToolResult.error("web.search", AgentStringUtils.trimToEmpty(exception.getMessage()));
        }
    }

    public AgentToolExecutionService.ToolResult readWebPage(Map<String, Object> arguments,
                                                            AgentToolExecutionService.ExecutionContext context) {
        String rawUrl = arg(arguments, "url");
        String url = normalizeWebUrl(rawUrl);
        if (url.isBlank()) {
            return AgentToolExecutionService.ToolResult.error("web.page.read", "url is invalid, only http/https is supported");
        }
        String username = context == null ? "" : AgentStringUtils.trimToEmpty(context.username());
        if (username.isBlank()) {
            return AgentToolExecutionService.ToolResult.error("web.page.read", "username is required");
        }
        if (!webAuthorizationService.isGranted(username, url)) {
            String requestId = webAuthorizationService.createOrReusePendingRequest(username, url);
            Map<String, Object> pending = new LinkedHashMap<>();
            pending.put("authorizationRequired", true);
            pending.put("requestId", requestId);
            pending.put("url", url);
            pending.put("message", "该 URL 需要用户授权后才可联网读取");
            pending.put("action", "POST /v1/user/ai/agent/web-access/resolve");
            return AgentToolExecutionService.ToolResult.ok("web.page.read", pending);
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Accept", "text/html,application/xhtml+xml")
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return AgentToolExecutionService.ToolResult.error("web.page.read", "page request failed: HTTP " + response.statusCode());
            }
            String body = response.body() == null ? "" : response.body();
            String title = extractHtmlTitle(body);
            String plainText = toPlainText(body);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("authorizationRequired", false);
            result.put("url", url);
            result.put("title", title);
            result.put("contentPreview", shrinkText(plainText, 2200));
            result.put("fetchedAt", Instant.now().toString());
            return AgentToolExecutionService.ToolResult.ok("web.page.read", result);
        } catch (Exception exception) {
            return AgentToolExecutionService.ToolResult.error("web.page.read", AgentStringUtils.trimToEmpty(exception.getMessage()));
        }
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

    private void collectWebSearchResults(Map<String, Object> payload,
                                         List<Map<String, Object>> collector,
                                         int limit) {
        if (payload == null || collector == null || collector.size() >= limit) {
            return;
        }
        String abstractUrl = normalizeWebUrl(AgentStringUtils.toStringValue(payload.get("AbstractURL")));
        String abstractText = AgentStringUtils.trimToEmpty(AgentStringUtils.toStringValue(payload.get("AbstractText")));
        String heading = AgentStringUtils.trimToEmpty(AgentStringUtils.toStringValue(payload.get("Heading")));
        if (!abstractUrl.isBlank()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("title", heading.isBlank() ? abstractUrl : heading);
            entry.put("url", abstractUrl);
            entry.put("snippet", abstractText);
            collector.add(entry);
        }
        Object relatedTopics = payload.get("RelatedTopics");
        if (relatedTopics instanceof List<?> list) {
            for (Object item : list) {
                if (collector.size() >= limit) {
                    break;
                }
                if (item instanceof Map<?, ?> itemMap) {
                    Object nestedTopics = itemMap.get("Topics");
                    if (nestedTopics instanceof List<?> nestedList) {
                        for (Object nested : nestedList) {
                            if (collector.size() >= limit) {
                                break;
                            }
                            collectSearchTopicItem(nested, collector);
                        }
                        continue;
                    }
                    collectSearchTopicItem(item, collector);
                }
            }
        }
    }

    private void collectSearchTopicItem(Object source, List<Map<String, Object>> collector) {
        if (!(source instanceof Map<?, ?> topicMap)) {
            return;
        }
        String resultUrl = normalizeWebUrl(AgentStringUtils.toStringValue(topicMap.get("FirstURL")));
        if (resultUrl.isBlank()) {
            return;
        }
        String text = AgentStringUtils.trimToEmpty(AgentStringUtils.toStringValue(topicMap.get("Text")));
        String title = text;
        String snippet = "";
        int splitIndex = text.indexOf(" - ");
        if (splitIndex > 0) {
            title = text.substring(0, splitIndex).trim();
            snippet = text.substring(splitIndex + 3).trim();
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("title", title.isBlank() ? resultUrl : title);
        entry.put("url", resultUrl);
        entry.put("snippet", snippet);
        collector.add(entry);
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

    private String normalizeWebUrl(String rawUrl) {
        String safe = AgentStringUtils.trimToEmpty(rawUrl);
        if (safe.isBlank()) {
            return "";
        }
        if (!safe.startsWith("http://") && !safe.startsWith("https://")) {
            safe = "https://" + safe;
        }
        try {
            URI uri = URI.create(safe);
            String scheme = AgentStringUtils.trimToEmpty(uri.getScheme()).toLowerCase();
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return "";
            }
            String host = AgentStringUtils.trimToEmpty(uri.getHost()).toLowerCase();
            if (host.isBlank()) {
                return "";
            }
            URI normalized = new URI(
                    scheme,
                    uri.getUserInfo(),
                    host,
                    uri.getPort(),
                    uri.getPath(),
                    uri.getQuery(),
                    null
            );
            return normalized.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String extractHtmlTitle(String html) {
        String source = html == null ? "" : html;
        String lower = source.toLowerCase();
        int start = lower.indexOf("<title>");
        int end = lower.indexOf("</title>");
        if (start >= 0 && end > start) {
            return source.substring(start + 7, end).trim();
        }
        return "";
    }

    private String toPlainText(String html) {
        String source = html == null ? "" : html;
        String noScript = source.replaceAll("(?is)<script[^>]*>.*?</script>", " ");
        String noStyle = noScript.replaceAll("(?is)<style[^>]*>.*?</style>", " ");
        String noTag = noStyle.replaceAll("(?is)<[^>]+>", " ");
        return noTag.replaceAll("\\s+", " ").trim();
    }

    private String shrinkText(String text, int maxLength) {
        String safe = AgentStringUtils.trimToEmpty(text);
        if (safe.length() <= Math.max(64, maxLength)) {
            return safe;
        }
        return safe.substring(0, Math.max(64, maxLength)) + "...";
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
