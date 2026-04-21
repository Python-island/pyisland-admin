package com.pyisland.server.auth.config;

import com.pyisland.server.auth.security.JsonAccessDeniedHandler;
import com.pyisland.server.auth.security.JsonAuthenticationEntryPoint;
import com.pyisland.server.auth.security.ClientVersionGateFilter;
import com.pyisland.server.auth.security.JwtAuthenticationFilter;
import com.pyisland.server.auth.security.ReplayProtectionFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 配置。
 * 无状态 JWT 鉴权：所有状态均通过 token + DB session_token 校验，不使用 HTTP Session。
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * 密码哈希器：使用 BCrypt 强哈希。
     * @return PasswordEncoder 实例。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * CORS 配置：允许任意来源并支持凭据（与旧 WebConfig.addCorsMappings 行为一致）。
     * @return CORS 配置源。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * 安全过滤器链。
     * 放行 /auth/**、版本查询、服务状态查询、头像上传等公共接口；/v1/user/** 允许登录用户；
     * 其余路径默认要求 ADMIN。
     * @param http HttpSecurity。
     * @param jwtFilter JWT 过滤器。
     * @param entryPoint 401 处理器。
     * @param accessDeniedHandler 403 处理器。
     * @return SecurityFilterChain。
     * @throws Exception 配置失败时抛出。
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   ClientVersionGateFilter clientVersionGateFilter,
                                                   JwtAuthenticationFilter jwtFilter,
                                                   ReplayProtectionFilter replayProtectionFilter,
                                                   JsonAuthenticationEntryPoint entryPoint,
                                                   JsonAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(reg -> reg
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/version", "/v1/version/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/version/update-count").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/service-status", "/v1/service-status/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/upload/user-avatar").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/v1/upload/feedback-log").hasAnyRole("USER", "ADMIN")
                        .requestMatchers("/v1/user/**").hasAnyRole("USER", "ADMIN")
                        .anyRequest().hasRole("ADMIN"))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(clientVersionGateFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(replayProtectionFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
