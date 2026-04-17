package com.pyisland.server;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * WAR 部署初始化器。
 */
public class ServletInitializer extends SpringBootServletInitializer {

    /**
     * 配置外部容器启动时的应用主类。
     * @param application Spring 应用构建器。
     * @return 配置后的构建器。
     */
    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(ServerApplication.class);
    }

}
