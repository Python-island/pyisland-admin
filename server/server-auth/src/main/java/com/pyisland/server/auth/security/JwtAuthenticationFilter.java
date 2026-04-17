package com.pyisland.server.auth.security;

import tools.jackson.databind.ObjectMapper;
import com.pyisland.server.user.entity.User;
import com.pyisland.server.user.service.UserService;
import com.pyisland.server.auth.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * JWT 鉴权过滤器。
 * 从 Authorization: Bearer 头解析 token，校验签名/过期/session_token/账号状态，
 * 成功时写入 SecurityContext；失败时直接写入 JSON 响应并中断。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 会话已在其他设备登录时使用的扩展业务码。 */
    public static final int CODE_SESSION_KICKED = 4011;

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    /**
     * 构造过滤器。
     * @param jwtUtil JWT 工具。
     * @param userService 用户服务。
     * @param objectMapper Jackson 序列化器。
     */
    public JwtAuthenticationFilter(JwtUtil jwtUtil,
                                   UserService userService,
                                   ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    /**
     * 鉴权主流程。
     * @param request HTTP 请求。
     * @param response HTTP 响应。
     * @param filterChain 过滤链。
     * @throws ServletException 过滤器异常。
     * @throws IOException IO 异常。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = authHeader.substring(7).trim();
        if (token.isEmpty() || !jwtUtil.validateToken(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        String username;
        String role;
        try {
            username = jwtUtil.getUsernameFromToken(token);
            role = jwtUtil.getRoleFromToken(token);
        } catch (Exception ex) {
            log.debug("JWT parse failed: {}", ex.getMessage());
            filterChain.doFilter(request, response);
            return;
        }

        User user = userService.getByUsername(username);
        if (user == null) {
            writeError(response, 401, "账号已被删除");
            return;
        }
        if (Boolean.FALSE.equals(user.getEnabled())) {
            writeError(response, 401, "账号已被禁用");
            return;
        }
        if (user.getSessionToken() != null && !token.equals(user.getSessionToken())) {
            writeError(response, CODE_SESSION_KICKED, "账号已在其他设备登录");
            return;
        }

        String effectiveRole = user.getRole() != null ? user.getRole() : role;
        if (effectiveRole == null || effectiveRole.isBlank()) {
            effectiveRole = User.ROLE_USER;
        }
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority(
                "ROLE_" + effectiveRole.toUpperCase(Locale.ROOT));
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                user.getUsername(), null, List.of(authority));
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);

        // 向下游控制器暴露，兼容历史 request.getAttribute("username") 读取点。
        request.setAttribute("username", user.getUsername());
        request.setAttribute("role", effectiveRole);
        request.setAttribute("userId", user.getId());

        filterChain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
