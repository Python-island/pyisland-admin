package com.pyisland.server.agent.config;

import com.pyisland.server.agent.controller.AgentRealtimeSttWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class AgentRealtimeSttWebSocketConfig implements WebSocketConfigurer {

    private final AgentRealtimeSttWebSocketHandler agentRealtimeSttWebSocketHandler;

    public AgentRealtimeSttWebSocketConfig(AgentRealtimeSttWebSocketHandler agentRealtimeSttWebSocketHandler) {
        this.agentRealtimeSttWebSocketHandler = agentRealtimeSttWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(agentRealtimeSttWebSocketHandler, "/v1/user/ai/stt/realtime")
                .setAllowedOriginPatterns("*");
    }
}
