package com.pyisland.server;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 后端服务启动入口。
 */
@SpringBootApplication
@MapperScan("com.pyisland.server.repository")
public class ServerApplication {

    /**
     * 启动 Spring Boot 应用。
     * @param args 启动参数。
     */
    public static void main(String[] args) {
        SpringApplication.run(ServerApplication.class, args);
    }

}
