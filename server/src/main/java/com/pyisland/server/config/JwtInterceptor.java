package com.pyisland.server.config;

import com.pyisland.server.repository.AdminUserMapper;
import com.pyisland.server.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 鉴权拦截器。
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final AdminUserMapper adminUserMapper;

    /**
     * 构造 JWT 拦截器。
     * @param jwtUtil JWT 工具。
     * @param adminUserMapper 管理员数据访问接口。
     */
    public JwtInterceptor(JwtUtil jwtUtil, AdminUserMapper adminUserMapper) {
        this.jwtUtil = jwtUtil;
        this.adminUserMapper = adminUserMapper;
    }

    /**
     * 对请求执行鉴权校验。
     * @param request HTTP 请求。
     * @param response HTTP 响应。
     * @param handler 处理器对象。
     * @return 是否放行。
     * @throws Exception 写响应失败时抛出。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String method = request.getMethod();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        String uri = request.getRequestURI();
        if ("GET".equalsIgnoreCase(method) && uri.startsWith("/api/v1/version")) {
            return true;
        }
        if ("POST".equalsIgnoreCase(method) && "/api/v1/version/update-count".equals(uri)) {
            return true;
        }
        if ("GET".equalsIgnoreCase(method) && uri.startsWith("/api/v1/service-status")) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtUtil.validateToken(token)) {
                String role = jwtUtil.getRoleFromToken(token);
                if (!"admin".equals(role)) {
                    response.setStatus(403);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":403,\"message\":\"无管理员权限\"}");
                    return false;
                }
                String username = jwtUtil.getUsernameFromToken(token);
                var user = adminUserMapper.selectByUsername(username);
                if (user == null) {
                    response.setStatus(401);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":401,\"message\":\"用户已被删除\"}");
                    return false;
                }
                if (user.getSessionToken() != null && !token.equals(user.getSessionToken())) {
                    response.setStatus(401);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":4011,\"message\":\"账号已在其他设备登录\"}");
                    return false;
                }
                request.setAttribute("username", username);
                return true;
            }
        }

        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"未登录或token已过期\"}");
        return false;
    }
}
