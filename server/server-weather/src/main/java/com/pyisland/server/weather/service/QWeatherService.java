package com.pyisland.server.weather.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

/**
 * 和风天气代理服务：
 * 1) 由服务端持有私钥并签发 JWT；
 * 2) 服务端请求和风 API；
 * 3) 响应写入 Redis 缓存，减少上游压力与延迟。
 */
@Service
public class QWeatherService {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final StringRedisTemplate qweatherRedisTemplate;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private final boolean enabled;
    private final String host;
    private final String privateKeyPath;
    private final String projectId;
    private final String keyId;
    private final String jwtIssuer;
    private final long jwtTtlSeconds;
    private final long dailyCacheTtlSeconds;
    private final long alertCacheTtlSeconds;
    private final String cacheKeyPrefix;

    private volatile String loadedKeyPath;
    private volatile PrivateKey cachedPrivateKey;

    public QWeatherService(
            @Qualifier("qweatherRedisTemplate") StringRedisTemplate qweatherRedisTemplate,
            @Value("${QWEATHER_ENABLED:true}") boolean enabled,
            @Value("${QWEATHER_HOST:https://devapi.qweather.com}") String host,
            @Value("${QWEATHER_PRIVATE_KEY_PATH:/etc/eisland/qweather/ed25519-private.pem}") String privateKeyPath,
            @Value("${QWEATHER_PROJECT_ID:}") String projectId,
            @Value("${QWEATHER_KEY_ID:}") String keyId,
            @Value("${QWEATHER_JWT_ISSUER:}") String jwtIssuer,
            @Value("${QWEATHER_JWT_TTL_SECONDS:600}") long jwtTtlSeconds,
            @Value("${QWEATHER_DAILY_CACHE_TTL_SECONDS:600}") long dailyCacheTtlSeconds,
            @Value("${QWEATHER_ALERT_CACHE_TTL_SECONDS:180}") long alertCacheTtlSeconds,
            @Value("${QWEATHER_CACHE_KEY_PREFIX:qweather}") String cacheKeyPrefix,
            @Value("${QWEATHER_CONNECT_TIMEOUT_MS:5000}") int connectTimeoutMs
    ) {
        this.qweatherRedisTemplate = qweatherRedisTemplate;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1000, connectTimeoutMs)))
                .build();
        this.enabled = enabled;
        this.host = trimTrailingSlash(host);
        this.privateKeyPath = safe(privateKeyPath);
        this.projectId = safe(projectId);
        this.keyId = safe(keyId);
        this.jwtIssuer = safe(jwtIssuer);
        this.jwtTtlSeconds = Math.max(60, jwtTtlSeconds);
        this.dailyCacheTtlSeconds = Math.max(30, dailyCacheTtlSeconds);
        this.alertCacheTtlSeconds = Math.max(30, alertCacheTtlSeconds);
        this.cacheKeyPrefix = safe(cacheKeyPrefix).isBlank() ? "qweather" : safe(cacheKeyPrefix);
    }

    public Map<String, Object> getThreeDayForecast(String location, String lang, String unit) {
        String normalizedLocation = requireNonBlank(location, "location 参数不能为空");
        String normalizedLang = safe(lang).isBlank() ? "zh" : safe(lang);
        String normalizedUnit = safe(unit).isBlank() ? "m" : safe(unit);
        String cacheKey = cacheKeyPrefix + ":daily3d:" + normalizedLocation + ":" + normalizedLang + ":" + normalizedUnit;

        Map<String, Object> cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        Map<String, String> query = new LinkedHashMap<>();
        query.put("location", normalizedLocation);
        query.put("lang", normalizedLang);
        query.put("unit", normalizedUnit);

        Map<String, Object> data = requestQWeather("/v7/weather/3d", query);
        writeCache(cacheKey, data, dailyCacheTtlSeconds);
        return data;
    }

    public Map<String, Object> getCurrentAlerts(String location, String lang) {
        String normalizedLocation = requireNonBlank(location, "location 参数不能为空");
        String normalizedLang = safe(lang).isBlank() ? "zh" : safe(lang);
        String cacheKey = cacheKeyPrefix + ":alerts:" + normalizedLocation + ":" + normalizedLang;

        Map<String, Object> cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        Map<String, String> query = new LinkedHashMap<>();
        query.put("location", normalizedLocation);
        query.put("lang", normalizedLang);

        Map<String, Object> data = requestQWeather("/v7/warning/now", query);
        writeCache(cacheKey, data, alertCacheTtlSeconds);
        return data;
    }

    private Map<String, Object> requestQWeather(String path, Map<String, String> query) {
        ensureEnabled();
        try {
            String queryString = query.entrySet().stream()
                    .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                    .collect(Collectors.joining("&"));
            URI uri = URI.create(host + path + "?" + queryString);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .header("Authorization", "Bearer " + generateJwt())
                    .header("Accept", "application/json")
                    .header("Accept-Encoding", "gzip")
                    .GET()
                    .timeout(Duration.ofSeconds(8))
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            String body = decodeBody(response);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("和风天气请求失败: HTTP " + response.statusCode());
            }
            if (body.isBlank()) {
                throw new IllegalStateException("和风天气返回空响应");
            }
            Map<String, Object> payload = objectMapper.readValue(body, MAP_TYPE);
            Object code = payload.get("code");
            String codeStr = code == null ? "" : String.valueOf(code).trim();
            if (!"200".equals(codeStr)) {
                throw new IllegalStateException("和风天气业务码异常: " + codeStr);
            }
            return payload;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("和风天气服务不可用: 请求被中断", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("和风天气服务不可用: " + safeError(ex), ex);
        }
    }

    private String decodeBody(HttpResponse<byte[]> response) throws Exception {
        byte[] bytes = response.body() == null ? new byte[0] : response.body();
        String encoding = response.headers()
                .firstValue("Content-Encoding")
                .orElse("")
                .toLowerCase(Locale.ROOT);
        boolean gzip = encoding.contains("gzip")
                || (bytes.length >= 2 && (bytes[0] & 0xff) == 0x1f && (bytes[1] & 0xff) == 0x8b);
        if (!gzip) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        try (GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            return new String(gzipInputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String generateJwt() {
        if (projectId.isBlank()) {
            throw new IllegalStateException("QWEATHER_PROJECT_ID 未配置");
        }
        if (keyId.isBlank()) {
            throw new IllegalStateException("QWEATHER_KEY_ID 未配置");
        }

        try {
            long iat = Instant.now().getEpochSecond();
            long exp = iat + jwtTtlSeconds;

            Map<String, Object> header = new LinkedHashMap<>();
            header.put("alg", "EdDSA");
            header.put("typ", "JWT");
            header.put("kid", keyId);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sub", projectId);
            payload.put("iat", iat);
            payload.put("exp", exp);
            if (!jwtIssuer.isBlank()) {
                payload.put("iss", jwtIssuer);
            }

            String encodedHeader = base64Url(objectMapper.writeValueAsBytes(header));
            String encodedPayload = base64Url(objectMapper.writeValueAsBytes(payload));
            String signingInput = encodedHeader + "." + encodedPayload;

            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(resolvePrivateKey());
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            String encodedSignature = base64Url(signature.sign());

            return signingInput + "." + encodedSignature;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("和风天气 JWT 生成失败", ex);
        }
    }

    private PrivateKey resolvePrivateKey() {
        if (privateKeyPath.isBlank()) {
            throw new IllegalStateException("QWEATHER_PRIVATE_KEY_PATH 未配置");
        }
        PrivateKey current = this.cachedPrivateKey;
        if (current != null && privateKeyPath.equals(loadedKeyPath)) {
            return current;
        }

        synchronized (this) {
            if (this.cachedPrivateKey != null && privateKeyPath.equals(loadedKeyPath)) {
                return this.cachedPrivateKey;
            }
            try {
                String pem = Files.readString(Path.of(privateKeyPath), StandardCharsets.UTF_8);
                String normalized = pem
                        .replace("-----BEGIN PRIVATE KEY-----", "")
                        .replace("-----END PRIVATE KEY-----", "")
                        .replaceAll("\\s", "");
                if (normalized.isBlank()) {
                    throw new IllegalStateException("QWeather 私钥文件为空");
                }
                byte[] der = Base64.getDecoder().decode(normalized);
                PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
                KeyFactory keyFactory = KeyFactory.getInstance("Ed25519");
                PrivateKey loaded = keyFactory.generatePrivate(spec);
                this.cachedPrivateKey = loaded;
                this.loadedKeyPath = privateKeyPath;
                return loaded;
            } catch (IllegalStateException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new IllegalStateException("加载 QWeather 私钥失败", ex);
            }
        }
    }

    private Map<String, Object> readCache(String key) {
        try {
            String raw = qweatherRedisTemplate.opsForValue().get(key);
            if (raw == null || raw.isBlank()) {
                return null;
            }
            return objectMapper.readValue(raw, MAP_TYPE);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void writeCache(String key, Map<String, Object> value, long ttlSeconds) {
        try {
            qweatherRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), Duration.ofSeconds(ttlSeconds));
        } catch (Exception ignored) {
        }
    }

    private void ensureEnabled() {
        if (!enabled) {
            throw new IllegalStateException("和风天气服务未启用");
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String requireNonBlank(String value, String errorMessage) {
        String normalized = safe(value);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(errorMessage);
        }
        return normalized;
    }

    private String trimTrailingSlash(String value) {
        String normalized = safe(value);
        if (!StringUtils.hasText(normalized)) {
            return "https://devapi.qweather.com";
        }
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://" + normalized;
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String safeError(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message.length() > 200 ? message.substring(0, 200) : message;
    }
}
