package com.pyisland.server.config;

import com.pyisland.server.repository.AdminUserMapper;
import com.pyisland.server.repository.AppUserMapper;
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

    /**
     * 面向普通用户（role=user）开放的路径前缀。其余路径仍需 role=admin。
     */
    private static final String USER_SELF_PREFIX = "/api/v1/user/";

    private final JwtUtil jwtUtil;
    private final AdminUserMapper adminUserMapper;
    private final AppUserMapper appUserMapper;

    /**
     * 构造 JWT 拦截器。
     * @param jwtUtil JWT 工具。
     * @param adminUserMapper 管理员数据访问接口。
     * @param appUserMapper 普通用户数据访问接口。
     */
    public JwtInterceptor(JwtUtil jwtUtil,
                          AdminUserMapper adminUserMapper,
                          AppUserMapper appUserMapper) {
        this.jwtUtil = jwtUtil;
        this.adminUserMapper = adminUserMapper;
        this.appUserMapper = appUserMapper;
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
        if ("POST".equalsIgnoreCase(method) && "/api/v1/upload/user-avatar".equals(uri)) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtUtil.validateToken(token)) {
                String role = jwtUtil.getRoleFromToken(token);
                String username = jwtUtil.getUsernameFromToken(token);
                boolean isUserSelf = uri.startsWith(USER_SELF_PREFIX);
                if (isUserSelf) {
                    return authorizeUser(request, response, token, role, username);
                }
                return authorizeAdmin(request, response, token, role, username);
            }
        }

        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"未登录或token已过期\"}");
        return false;
    }

    /**
     * 管理员路径鉴权：仅 role=admin 且存在于 admin_user 表、session_token 匹配。
     */
    private boolean authorizeAdmin(HttpServletRequest request, HttpServletResponse response,
                                   String token, String role, String username) throws java.io.IOException {
        if (!"admin".equals(role)) {
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"无管理员权限\"}");
            return false;
        }
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
        request.setAttribute("role", role);
        return true;
    }

    /**
     * 用户自助路径鉴权：允许 role=user（查 app_user 表），也兼容 role=admin。
     */
    private boolean authorizeUser(HttpServletRequest request, HttpServletResponse response,
                                  String token, String role, String username) throws java.io.IOException {
        if ("admin".equals(role)) {
            var admin = adminUserMapper.selectByUsername(username);
            if (admin == null) {
                response.setStatus(401);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":401,\"message\":\"账号已被删除\"}");
                return false;
            }
            if (admin.getSessionToken() != null && !token.equals(admin.getSessionToken())) {
                response.setStatus(401);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":4011,\"message\":\"账号已在其他设备登录\"}");
                return false;
            }
            request.setAttribute("username", username);
            request.setAttribute("role", role);
            return true;
        }
        if (!"user".equals(role)) {
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"无权限访问\"}");
            return false;
        }
        var user = appUserMapper.selectByUsername(username);
        if (user == null) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"账号已被删除\"}");
            return false;
        }
        if (user.getSessionToken() != null && !token.equals(user.getSessionToken())) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":4011,\"message\":\"账号已在其他设备登录\"}");
            return false;
        }
        request.setAttribute("username", username);
        request.setAttribute("role", role);
        return true;
    }
}
