package com.pyisland.server.auth.security;

import tools.jackson.databind.ObjectMapper;
import com.pyisland.server.version.entity.AppVersion;
import com.pyisland.server.version.service.AppVersionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 客户端版本门禁过滤器。
 * 客户端接口必须携带 X-Client-Version 且等于服务端记录的最新版本。
 */
@Component
public class ClientVersionGateFilter extends OncePerRequestFilter {

    public static final String APP_NAME_HEADER = "X-App-Name";
    public static final String CLIENT_VERSION_HEADER = "X-Client-Version";

    private final AppVersionService appVersionService;
    private final ObjectMapper objectMapper;

    public ClientVersionGateFilter(AppVersionService appVersionService,
                                   ObjectMapper objectMapper) {
        this.appVersionService = appVersionService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!isClientApi(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String appName = normalize(request.getHeader(APP_NAME_HEADER));
        if (appName == null) {
            writeError(response, 4262, "缺少应用标识头");
            return;
        }

        String clientVersion = normalize(request.getHeader(CLIENT_VERSION_HEADER));
        if (clientVersion == null) {
            writeError(response, 4261, "缺少客户端版本头");
            return;
        }

        AppVersion latest = appVersionService.getVersion(appName);
        String latestVersion = latest == null ? null : normalize(latest.getVersion());
        if (latestVersion == null) {
            writeError(response, 5031, "服务端未配置该应用可用版本");
            return;
        }

        if (!latestVersion.equals(clientVersion)) {
            writeError(response, 4260, "客户端版本过旧，请升级至最新版本");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isClientApi(String uri) {
        if (uri == null || uri.isBlank()) return false;
        return uri.startsWith("/auth/user/")
                || uri.startsWith("/v1/user/")
                || "/v1/upload/user-avatar".equals(uri);
    }

    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void writeError(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UPGRADE_REQUIRED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
