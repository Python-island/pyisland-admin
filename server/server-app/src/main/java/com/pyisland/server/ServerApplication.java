package com.pyisland.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * 后端服务启动入口。
 * 以 com.pyisland.server 为根包进行组件扫描；各业务模块的 Mapper 接口通过
 * {@code @Mapper} 注解由 mybatis-spring-boot-starter 自动发现并注册。
 */
@SpringBootApplication
@EnableCaching
public class ServerApplication {

    /**
     * 启动 Spring Boot 应用。
     * @param args 启动参数。
     */
    public static void main(String[] args) {
        SpringApplication.run(ServerApplication.class, args);
    }

}
