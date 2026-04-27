package com.pyisland.server.agent.service;

import com.pyisland.server.agent.utils.AgentStringUtils;
import com.pyisland.server.agent.utils.AgentToolUtils;
import com.pyisland.server.weather.service.QWeatherService;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Agent 工具执行器（Phase 4: ReAct）。
 */
@Service
public class AgentToolExecutionService {

    private final AgentToolUtils toolUtils;

    public AgentToolExecutionService(QWeatherService qWeatherService,
                                     AgentWebAuthorizationService webAuthorizationService) {
        this.toolUtils = new AgentToolUtils(qWeatherService, webAuthorizationService);
    }

    public ToolResult execute(String toolName,
                              Map<String, Object> arguments,
                              boolean proUser,
                              ExecutionContext context) {
        String safeToolName = AgentStringUtils.trimToEmpty(toolName);
        if (safeToolName.isBlank()) {
            return ToolResult.error("unknown", "tool name is empty");
        }
        if ("user.ip.get".equals(safeToolName)) {
            return toolUtils.getUserIp(context);
        }
        if ("location.by_ip.resolve".equals(safeToolName)) {
            return toolUtils.resolveLocationByIp(arguments, context);
        }
        if ("weather.query".equals(safeToolName)) {
            if (!proUser) {
                return ToolResult.error("weather.query", "weather tool requires pro user");
            }
            return toolUtils.queryWeather(arguments);
        }
        if ("time.now".equals(safeToolName)) {
            return toolUtils.getCurrentTime();
        }
        if ("session.context.get".equals(safeToolName)) {
            return toolUtils.getSessionContext(context);
        }
        if ("web.search".equals(safeToolName)) {
            return toolUtils.searchWeb(arguments);
        }
        if ("web.page.read".equals(safeToolName)) {
            return toolUtils.readWebPage(arguments, context);
        }
        if ("weather.city.lookup".equals(safeToolName)) {
            if (!proUser) {
                return ToolResult.error("weather.city.lookup", "weather tool requires pro user");
            }
            return toolUtils.lookupWeatherCity(arguments);
        }
        if ("weather.by_city.query".equals(safeToolName)) {
            if (!proUser) {
                return ToolResult.error("weather.by_city.query", "weather tool requires pro user");
            }
            return toolUtils.queryWeatherByCity(arguments);
        }
        if ("weather.quota.status".equals(safeToolName)) {
            if (!proUser) {
                return ToolResult.error("weather.quota.status", "weather tool requires pro user");
            }
            return toolUtils.queryWeatherQuotaStatus();
        }
        return ToolResult.error(safeToolName, "tool not supported");
    }

    public record ExecutionContext(String username, String clientIp) {
    }

    public record ToolResult(String tool, boolean success, Object data, String error) {

        public static ToolResult ok(String tool, Object data) {
            return new ToolResult(tool, true, data, "");
        }

        public static ToolResult error(String tool, String error) {
            return new ToolResult(tool, false, Map.of(), error == null ? "" : error.trim());
        }
    }
}
