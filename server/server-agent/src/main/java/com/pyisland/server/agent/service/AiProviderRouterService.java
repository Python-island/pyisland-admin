package com.pyisland.server.agent.service;

import com.pyisland.server.agent.config.MihtnelisAgentProperties;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * 供应商路由服务（Phase B 骨架）。
 */
@Service
public class AiProviderRouterService {

    private final MihtnelisAgentProperties properties;

    public AiProviderRouterService(MihtnelisAgentProperties properties) {
        this.properties = properties;
    }

    /**
     * 解析本次请求使用的供应商。
     *
     * @param requestedProvider 用户指定供应商。
     * @return 供应商编码。
     */
    public String resolveProvider(String requestedProvider) {
        List<String> allowed = properties.getAllowedProviders();
        String normalizedDefault = normalize(properties.getDefaultProvider());
        if (normalizedDefault.isBlank()) {
            normalizedDefault = "auto";
        }

        String normalizedRequested = normalize(requestedProvider);
        if (normalizedRequested.isBlank()) {
            return normalizedDefault;
        }
        if (allowed == null || allowed.isEmpty()) {
            return normalizedRequested;
        }
        for (String item : allowed) {
            if (normalize(item).equals(normalizedRequested)) {
                return normalizedRequested;
            }
        }
        return normalizedDefault;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
