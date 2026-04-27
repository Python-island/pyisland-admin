package com.pyisland.server.agent.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * mihtnelis agent 配置注册。
 */
@Configuration
@EnableConfigurationProperties(MihtnelisAgentProperties.class)
public class MihtnelisAgentConfiguration {
}
