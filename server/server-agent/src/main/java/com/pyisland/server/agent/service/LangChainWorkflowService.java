package com.pyisland.server.agent.service;

import org.springframework.stereotype.Service;

/**
 * LangChain 工作流服务（Phase 3 基础版）。
 */
@Service
public class LangChainWorkflowService {

    /**
     * 生成系统提示词。
     *
     * @param proUser 是否 Pro 用户。
     * @return 系统提示词。
     */
    public String buildSystemPrompt(boolean proUser) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是 mihtnelis agent，是eisland软件内置agent，eisland是一个基于windows的灵动岛。你必须遵循 ReAct 工具调用协议。\n")
                .append("可用工具：\n")
                .append("1) user.ip.get: 入参 {}，返回用户公网 IP。\n")
                .append("2) location.by_ip.resolve: 入参 {\"ip\":\"1.2.3.4\"}，返回城市和天气 location。\n")
                .append("3) weather.query: 入参 {\"location\":\"101010100\"}，返回天气与预警。\n")
                .append("4) weather.city.lookup: 入参 {\"query\":\"上海\"}，返回城市匹配和 location。\n")
                .append("5) weather.by_city.query: 入参 {\"query\":\"上海\"}，自动查城市并返回天气与预警。\n")
                .append("6) weather.quota.status: 入参 {}，返回天气接口月配额状态。\n")
                .append("7) time.now: 入参 {}，返回当前系统时间与时区。\n")
                .append("8) session.context.get: 入参 {}，返回当前会话上下文（username/clientIp/timestamp）。\n")
                .append("9) web.search: 入参 {\"query\":\"关键词\",\"limit\":5}，返回联网搜索结果（标题/url/摘要）。\n")
                .append("10) web.page.read: 入参 {\"url\":\"https://...\"}，读取网页正文（需用户授权 URL 访问）。\n")
                .append("11) file.list: 入参 {\"path\":\"C:/...\",\"limit\":200}，列出本地目录内容（客户端执行）。\n")
                .append("12) file.read: 入参 {\"path\":\"C:/.../a.txt\"}，读取本地文件文本（客户端执行）。\n")
                .append("13) file.write: 入参 {\"path\":\"C:/.../a.txt\",\"content\":\"...\"}，写入本地文件（客户端执行）。\n")
                .append("14) file.delete: 入参 {\"path\":\"C:/.../a.txt\"}，删除本地文件或目录（客户端执行）。\n")
                .append("15) cmd.exec: 入参 {\"command\":\"dir\",\"cwd\":\"C:/...\",\"timeoutMs\":20000}，执行本地命令（客户端执行）。\n");
        if (!proUser) {
            prompt.append("当前用户不是 Pro，禁止调用 weather.query、weather.city.lookup、weather.by_city.query、weather.quota.status。\n");
        }
        prompt.append("输出规则（必须二选一，且仅输出 JSON，不要 Markdown）：\n")
                .append("A. 调工具时输出：")
                .append("{\"type\":\"tool_call\",\"tool\":\"user.ip.get\",\"arguments\":{}}\n")
                .append("B. 最终回答时输出：")
                .append("{\"type\":\"final\",\"answer\":\"...\"}\n")
                .append("若用户问天气，优先按链路调用：user.ip.get -> location.by_ip.resolve -> weather.query -> final；若用户提供城市名，优先 weather.by_city.query。\n")
                .append("若需联网信息，先用 web.search，再按需调用 web.page.read；当 web.page.read 返回 authorizationRequired=true 时，先提醒用户点击授权按钮，不要伪造网页内容。\n")
                .append("涉及本地文件和命令行时，优先使用 file.list/file.read/file.write/file.delete/cmd.exec；这些工具由客户端执行，请基于工具结果再继续回答。\n")
                .append("最终回答用中文，简洁准确。");
        return prompt.toString();
    }

    /**
     * 构造用户提示词。
     *
     * @param userPrompt 原始用户输入。
     * @param provider   当前供应商。
     * @return 组合后提示词。
     */
    public String buildUserPrompt(String userPrompt, String context, String provider) {
        String safePrompt = userPrompt == null ? "" : userPrompt.trim();
        String safeContext = context == null ? "" : context.trim();
        String safeProvider = provider == null ? "auto" : provider.trim();
        if (safeContext.isBlank()) {
            return "provider=" + safeProvider + "\n\n" + safePrompt;
        }
        return "provider=" + safeProvider
                + "\n\nConversation Context:\n" + safeContext
                + "\n\nUser Question:\n" + safePrompt;
    }

    public String buildReActUserPrompt(String userPrompt, String context, String provider, String scratchpad) {
        String safePrompt = userPrompt == null ? "" : userPrompt.trim();
        String safeContext = context == null ? "" : context.trim();
        String safeProvider = provider == null ? "auto" : provider.trim();
        String safeScratchpad = scratchpad == null ? "" : scratchpad.trim();
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("provider=").append(safeProvider).append("\n\n");
        if (!safeContext.isBlank()) {
            promptBuilder.append("Conversation Context:\n")
                    .append(safeContext)
                    .append("\n\n");
        }
        promptBuilder.append("User Question:\n").append(safePrompt);
        if (safeScratchpad.isBlank()) {
            return promptBuilder.toString();
        }
        promptBuilder.append("\n\nPrevious Reasoning / Observations:\n")
                .append(safeScratchpad)
                .append("\n\n请基于上面的 Observation 决定下一步：继续 tool_call 或给 final。");
        return promptBuilder.toString();
    }

    public String buildNativeToolSystemPrompt(boolean proUser) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是 mihtnelis agent，是 eisland 软件内置 agent。\n")
                .append("你可以按需调用工具帮助回答：userIpGet、locationByIpResolve、weatherQuery、weatherCityLookup、weatherByCityQuery、weatherQuotaStatus、timeNow、sessionContextGet、webSearch、webPageRead、fileList、fileRead、fileWrite、fileDelete、cmdExec。\n")
                .append("回答要求：中文、简洁、准确，不输出 markdown 代码块。\n");
        if (!proUser) {
            prompt.append("当前用户不是 Pro，禁止调用 weatherQuery、weatherCityLookup、weatherByCityQuery、weatherQuotaStatus。\n");
        }
        prompt.append("如果用户询问天气，优先使用工具链：userIpGet -> locationByIpResolve -> weatherQuery；若用户直接给城市名，优先 weatherByCityQuery。\n")
                .append("若需联网检索，优先 webSearch；读取具体 URL 时使用 webPageRead，且必须等待用户授权。\n")
                .append("涉及本地文件或命令时，使用 fileList/fileRead/fileWrite/fileDelete/cmdExec，这些工具在客户端执行。\n")
                .append("当工具返回失败时，解释原因并给可执行建议。\n");
        return prompt.toString();
    }
}
