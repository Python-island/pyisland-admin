package com.pyisland.server.agent.service;

import com.pyisland.server.agent.utils.AgentStringUtils;
import com.pyisland.server.agent.utils.AgentToolUtils;
import com.pyisland.server.weather.service.QWeatherService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
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
        Map<String, Object> safeArguments = arguments == null ? Map.of() : arguments;
        notifyToolCallStart(context, safeToolName, safeArguments);

        ToolResult result;
        if (isClientLocalTool(safeToolName)) {
            String username = context == null ? "" : AgentStringUtils.trimToEmpty(context.username());
            if (username.isBlank()) {
                result = ToolResult.error(safeToolName, "username is required for local tool execution");
                notifyToolCallResult(context, safeToolName, safeArguments, result);
                return result;
            }
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
                    result = ToolResult.ok(safeToolName, data);
                    notifyToolCallResult(context, safeToolName, safeArguments, result);
                    return result;
                }
                result = ToolResult.error(safeToolName, AgentStringUtils.trimToDefault(payload.error(), "local tool execution failed"));
                notifyToolCallResult(context, safeToolName, safeArguments, result);
                return result;
            }
            result = ToolResult.ok(safeToolName, Map.of(
                    "localToolExecutionRequired", true,
                    "tool", safeToolName,
                    "arguments", safeArguments
            ));
            notifyToolCallResult(context, safeToolName, safeArguments, result);
            return result;
        }
        if ("user.ip.get".equals(safeToolName)) {
            result = toolUtils.getUserIp(context);
            notifyToolCallResult(context, safeToolName, safeArguments, result);
            return result;
        }
        if ("location.by_ip.resolve".equals(safeToolName)) {
            result = toolUtils.resolveLocationByIp(safeArguments, context);
            notifyToolCallResult(context, safeToolName, safeArguments, result);
            return result;
        }
        if ("weather.query".equals(safeToolName)) {
            if (!proUser) {
                result = ToolResult.error("weather.query", "weather tool requires pro user");
                notifyToolCallResult(context, safeToolName, safeArguments, result);
                return result;
            }
            result = toolUtils.queryWeather(safeArguments);
            notifyToolCallResult(context, safeToolName, safeArguments, result);
            return result;
        }
        if ("time.now".equals(safeToolName)) {
            result = toolUtils.getCurrentTime();
            notifyToolCallResult(context, safeToolName, safeArguments, result);
            return result;
        }
        if ("session.context.get".equals(safeToolName)) {
            result = toolUtils.getSessionContext(context);
            notifyToolCallResult(context, safeToolName, safeArguments, result);
            return result;
        }
        if ("web.search".equals(safeToolName)) {
            result = toolUtils.searchWeb(safeArguments);
            notifyToolCallResult(context, safeToolName, safeArguments, result);
            return result;
        }
        if ("web.page.read".equals(safeToolName)) {
            result = toolUtils.readWebPage(safeArguments, context);
            notifyToolCallResult(context, safeToolName, safeArguments, result);
            return result;
        }
        if ("weather.city.lookup".equals(safeToolName)) {
            if (!proUser) {
                result = ToolResult.error("weather.city.lookup", "weather tool requires pro user");
                notifyToolCallResult(context, safeToolName, safeArguments, result);
                return result;
            }
            result = toolUtils.lookupWeatherCity(safeArguments);
            notifyToolCallResult(context, safeToolName, safeArguments, result);
            return result;
        }
        if ("weather.by_city.query".equals(safeToolName)) {
            if (!proUser) {
                result = ToolResult.error("weather.by_city.query", "weather tool requires pro user");
                notifyToolCallResult(context, safeToolName, safeArguments, result);
                return result;
            }
            result = toolUtils.queryWeatherByCity(safeArguments);
            notifyToolCallResult(context, safeToolName, safeArguments, result);
            return result;
        }
        if ("weather.quota.status".equals(safeToolName)) {
            if (!proUser) {
                result = ToolResult.error("weather.quota.status", "weather tool requires pro user");
                notifyToolCallResult(context, safeToolName, safeArguments, result);
                return result;
            }
            result = toolUtils.queryWeatherQuotaStatus();
            notifyToolCallResult(context, safeToolName, safeArguments, result);
            return result;
        }
        result = ToolResult.error(safeToolName, "tool not supported");
        notifyToolCallResult(context, safeToolName, safeArguments, result);
        return result;
    }

    private boolean isClientLocalTool(String toolName) {
        String safeToolName = AgentStringUtils.trimToEmpty(toolName).toLowerCase();
        return safeToolName.startsWith("file.") || safeToolName.startsWith("cmd.");
    }

    private void notifyToolCallStart(ExecutionContext context,
                                     String tool,
                                     Map<String, Object> arguments) {
        if (context == null || context.toolExecutionObserver() == null) {
            return;
        }
        context.toolExecutionObserver().onToolCallRequested(tool, cloneArguments(arguments));
    }

    private void notifyToolCallResult(ExecutionContext context,
                                      String tool,
                                      Map<String, Object> arguments,
                                      ToolResult result) {
        if (context == null || context.toolExecutionObserver() == null || result == null) {
            return;
        }
        context.toolExecutionObserver().onToolCallCompleted(tool, cloneArguments(arguments), result);
    }

    private Map<String, Object> cloneArguments(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return Map.of();
        }
        return new LinkedHashMap<>(arguments);
    }

    public record ExecutionContext(String username,
                                   String clientIp,
                                   ToolExecutionObserver toolExecutionObserver) {

        public ExecutionContext(String username, String clientIp) {
            this(username, clientIp, null);
        }
    }

    public interface ToolExecutionObserver {

        void onToolCallRequested(String toolName, Map<String, Object> arguments);

        void onToolCallCompleted(String toolName, Map<String, Object> arguments, ToolResult result);
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
