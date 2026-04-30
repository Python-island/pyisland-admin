package com.pyisland.server.agent.service;

import com.pyisland.server.agent.utils.AgentStringUtils;
import com.pyisland.server.agent.utils.AgentToolUtils;
import com.pyisland.server.weather.service.QWeatherService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
        if ("agent.todo.write".equals(safeToolName)) {
            result = writeAgentTodoList(safeArguments, context);
            notifyToolCallResult(context, safeToolName, safeArguments, result);
            return result;
        }
        result = ToolResult.error(safeToolName, "tool not supported");
        notifyToolCallResult(context, safeToolName, safeArguments, result);
        return result;
    }

    /**
     * 处理 agent.todo.write 虚拟工具：归一化 items 并通过 observer 推送 todo SSE 事件。
     */
    private ToolResult writeAgentTodoList(Map<String, Object> arguments, ExecutionContext context) {
        Object rawItems = arguments == null ? null : arguments.get("items");
        if (!(rawItems instanceof java.util.Collection<?> collection)) {
            return ToolResult.error("agent.todo.write", "items 必须是数组");
        }
        List<Map<String, Object>> normalized = new ArrayList<>();
        int autoId = 1;
        for (Object raw : collection) {
            if (!(raw instanceof Map<?, ?> rawMap)) {
                continue;
            }
            String content = AgentStringUtils.trimToEmpty(AgentStringUtils.toStringValue(rawMap.get("content")));
            if (content.isBlank()) {
                continue;
            }
            String id = AgentStringUtils.trimToEmpty(AgentStringUtils.toStringValue(rawMap.get("id")));
            if (id.isBlank()) {
                id = String.valueOf(autoId);
            }
            String status = AgentStringUtils.trimToEmpty(AgentStringUtils.toStringValue(rawMap.get("status"))).toLowerCase(Locale.ROOT);
            if (!"pending".equals(status) && !"in_progress".equals(status) && !"completed".equals(status)) {
                status = "pending";
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", id);
            item.put("content", content);
            item.put("status", status);
            normalized.add(item);
            autoId++;
        }
        if (normalized.isEmpty()) {
            return ToolResult.error("agent.todo.write", "items 为空");
        }
        if (context != null && context.toolExecutionObserver() != null) {
            context.toolExecutionObserver().onTodoUpdate(normalized);
        }
        long completed = normalized.stream().filter(it -> "completed".equals(it.get("status"))).count();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", normalized.size());
        data.put("completed", completed);
        data.put("items", normalized);
        return ToolResult.ok("agent.todo.write", data);
    }

    private boolean isClientLocalTool(String toolName) {
        String safeToolName = AgentStringUtils.trimToEmpty(toolName).toLowerCase();
        return safeToolName.startsWith("file.")
                || safeToolName.startsWith("cmd.")
                || safeToolName.startsWith("sys.")
                || "web.search".equals(safeToolName);
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

        default void onThinking(int turn, String content) {
        }

        /**
         * 实时推理内容增量回调（流式模式下使用）。
         *
         * @param deltaText 增量文本。
         * @param done      是否为最后一段推理内容。
         */
        default void onThinkingDelta(String deltaText, boolean done) {
        }

        /**
         * 实时正文内容增量回调（流式模式下使用）。
         *
         * @param deltaText 增量文本。
         * @param done      是否为最后一段正文内容。
         */
        default void onContentDelta(String deltaText, boolean done) {
        }

        /**
         * 通知前端清除已流式推送的正文内容（例如：content 实际是 tool call JSON 时需要回退）。
         */
        default void onContentReset() {
        }

        /**
         * agent.todo.write 触发的 TodoList 更新。
         * @param items 已归一化的 TodoList，每项包含 id/content/status。
         */
        default void onTodoUpdate(List<Map<String, Object>> items) {
        }
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
