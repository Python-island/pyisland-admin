package com.pyisland.server.security;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 未登录 / token 过期时返回统一 JSON 错误，保持与旧版响应结构兼容。
 */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    /**
     * 构造入口点。
     * @param objectMapper Jackson 序列化器。
     */
    public JsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 写入 401 错误响应。
     * @param request HTTP 请求。
     * @param response HTTP 响应。
     * @param authException 触发异常。
     * @throws IOException 写响应失败时抛出。
     */
    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        String message = request.getAttribute("auth_error_message") instanceof String msg
                ? msg
                : "未登录或token已过期";
        Object codeAttr = request.getAttribute("auth_error_code");
        int code = codeAttr instanceof Integer c ? c : 401;
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
