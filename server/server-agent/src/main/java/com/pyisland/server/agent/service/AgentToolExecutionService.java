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
    private final AgentLocalToolRelayService localToolRelayService;

    public AgentToolExecutionService(QWeatherService qWeatherService,
                                     AgentWebAuthorizationService webAuthorizationService,
                                     AgentLocalToolRelayService localToolRelayService) {
        this.toolUtils = new AgentToolUtils(qWeatherService, webAuthorizationService);
        this.localToolRelayService = localToolRelayService;
    }

    public ToolResult execute(String toolName,
                              Map<String, Object> arguments,
                              boolean proUser,
                              ExecutionContext context) {
        String safeToolName = AgentStringUtils.trimToEmpty(toolName);
        if (safeToolName.isBlank()) {
            return ToolResult.error("unknown", "tool name is empty");
        }
        if (isClientLocalTool(safeToolName)) {
            String username = context == null ? "" : AgentStringUtils.trimToEmpty(context.username());
            if (username.isBlank()) {
                return ToolResult.error(safeToolName, "username is required for local tool execution");
            }
            Map<String, Object> safeArguments = arguments == null ? Map.of() : arguments;
            AgentLocalToolRelayService.LocalToolExecutionPayload payload =
                    localToolRelayService.consumeResolvedTool(username, safeToolName, safeArguments);
            if (payload != null) {
                Map<String, Object> data = Map.of(
                        "requestId", payload.requestId(),
                        "tool", payload.tool(),
                        "arguments", payload.arguments(),
                        "argumentsDigest", payload.argumentsDigest(),
                        "durationMs", payload.durationMs(),
                        "resolvedAt", payload.resolvedAt(),
                        "source", "client-local-runtime",
                        "result", payload.result() == null ? Map.of() : payload.result()
                );
                if (payload.success()) {
                    return ToolResult.ok(safeToolName, data);
                }
                return ToolResult.error(safeToolName, AgentStringUtils.trimToDefault(payload.error(), "local tool execution failed"));
            }
            return ToolResult.ok(safeToolName, Map.of(
                    "localToolExecutionRequired", true,
                    "tool", safeToolName,
                    "arguments", safeArguments
            ));
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

    private boolean isClientLocalTool(String toolName) {
        String safeToolName = AgentStringUtils.trimToEmpty(toolName).toLowerCase();
        return safeToolName.startsWith("file.") || safeToolName.startsWith("cmd.");
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
