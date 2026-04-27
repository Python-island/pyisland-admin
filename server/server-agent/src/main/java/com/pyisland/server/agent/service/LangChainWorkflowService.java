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
                .append("10) web.page.read: 入参 {\"url\":\"https://...\"}，读取网页正文（需用户授权 URL 访问）。\n");
        if (!proUser) {
            prompt.append("当前用户不是 Pro，禁止调用 weather.query、weather.city.lookup、weather.by_city.query、weather.quota.status。\n");
        }
        prompt.append("输出规则（必须二选一，且仅输出 JSON，不要 Markdown）：\n")
                .append("A. 调工具时输出：")
                .append("{\"type\":\"tool_call\",\"tool\":\"user.ip.get\",\"arguments\":{}}\n")
                .append("B. 最终回答时输出：")
                .append("{\"type\":\"final\",\"answer\":\"...\"}\n")
                .append("当输出 final.answer 时，可以使用 <think>你的思考过程</think> + 面向用户可见答案。\n")
                .append("若用户问天气，优先按链路调用：user.ip.get -> location.by_ip.resolve -> weather.query -> final；若用户提供城市名，优先 weather.by_city.query。\n")
                .append("若需联网信息，先用 web.search，再按需调用 web.page.read；当 web.page.read 返回 authorizationRequired=true 时，先提醒用户点击授权按钮，不要伪造网页内容。\n")
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
    public String buildUserPrompt(String userPrompt, String provider) {
        String safePrompt = userPrompt == null ? "" : userPrompt.trim();
        String safeProvider = provider == null ? "auto" : provider.trim();
        return "provider=" + safeProvider + "\n\n" + safePrompt;
    }

    public String buildReActUserPrompt(String userPrompt, String provider, String scratchpad) {
        String safePrompt = userPrompt == null ? "" : userPrompt.trim();
        String safeProvider = provider == null ? "auto" : provider.trim();
        String safeScratchpad = scratchpad == null ? "" : scratchpad.trim();
        if (safeScratchpad.isBlank()) {
            return "provider=" + safeProvider + "\n\nUser Question:\n" + safePrompt;
        }
        return "provider=" + safeProvider
                + "\n\nUser Question:\n" + safePrompt
                + "\n\nPrevious Reasoning / Observations:\n" + safeScratchpad
                + "\n\n请基于上面的 Observation 决定下一步：继续 tool_call 或给 final。";
    }

    public String buildNativeToolSystemPrompt(boolean proUser) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是 mihtnelis agent，是 eisland 软件内置 agent。\n")
                .append("你可以按需调用工具帮助回答：userIpGet、locationByIpResolve、weatherQuery、weatherCityLookup、weatherByCityQuery、weatherQuotaStatus、timeNow、sessionContextGet、webSearch、webPageRead。\n")
                .append("回答要求：中文、简洁、准确，不输出 markdown 代码块。\n")
                .append("如果需要展示思考过程，请使用 <think>...</think> 标签包裹。\n");
        if (!proUser) {
            prompt.append("当前用户不是 Pro，禁止调用 weatherQuery、weatherCityLookup、weatherByCityQuery、weatherQuotaStatus。\n");
        }
        prompt.append("如果用户询问天气，优先使用工具链：userIpGet -> locationByIpResolve -> weatherQuery；若用户直接给城市名，优先 weatherByCityQuery。\n")
                .append("若需联网检索，优先 webSearch；读取具体 URL 时使用 webPageRead，且必须等待用户授权。\n")
                .append("当工具返回失败时，解释原因并给可执行建议。\n");
        return prompt.toString();
    }
}
