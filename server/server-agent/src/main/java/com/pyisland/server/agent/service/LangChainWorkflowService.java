package com.pyisland.server.agent.service;

import com.pyisland.server.agent.config.EdocPromptBuilder;
import com.pyisland.server.agent.config.MihtnelisPromptBuilder;
import com.pyisland.server.agent.config.R1pxcPromptBuilder;
import org.springframework.stereotype.Service;

/**
 * LangChain 工作流服务。
 */
@Service
public class LangChainWorkflowService {
    private final MihtnelisPromptBuilder mihtnelisPromptBuilder;
    private final R1pxcPromptBuilder r1pxcPromptBuilder;
    private final EdocPromptBuilder edocPromptBuilder;

    public LangChainWorkflowService(MihtnelisPromptBuilder mihtnelisPromptBuilder,
                                    R1pxcPromptBuilder r1pxcPromptBuilder,
                                    EdocPromptBuilder edocPromptBuilder) {
        this.mihtnelisPromptBuilder = mihtnelisPromptBuilder;
        this.r1pxcPromptBuilder = r1pxcPromptBuilder;
        this.edocPromptBuilder = edocPromptBuilder;
    }

    public String buildSystemPrompt(String agentMode, boolean proUser, java.util.List<String> workspaces, java.util.List<MihtnelisAgentStreamService.SkillEntry> skills, boolean snapshotMode) {
        String mode = agentMode == null ? "mihtnelis" : agentMode.trim().toLowerCase();
        return switch (mode) {
            case "r1pxc" -> r1pxcPromptBuilder.buildSystemPrompt(proUser, workspaces, skills, snapshotMode);
            case "edoc" -> edocPromptBuilder.buildSystemPrompt(proUser, workspaces, skills, snapshotMode);
            default -> mihtnelisPromptBuilder.buildSystemPrompt(proUser, workspaces, skills, snapshotMode);
        };
    }

    /**
     * 构造用户提示词
     */
    public String buildUserPrompt(String userPrompt, String context, String provider, String envMeta) {
        String safePrompt = userPrompt == null ? "" : userPrompt.trim();
        String safeContext = context == null ? "" : context.trim();
        String safeProvider = provider == null ? "auto" : provider.trim();
        String safeMeta = envMeta == null ? "" : envMeta.trim();

        String attachmentBlock = extractAttachmentBlock(safePrompt);
        String questionOnly = stripAttachmentTags(safePrompt);

        StringBuilder sb = new StringBuilder();
        sb.append("provider=").append(safeProvider).append("\n\n");

        if (!safeMeta.isBlank()) {
            sb.append("当前环境: ").append(safeMeta).append("\n\n");
        }
        if (!safeContext.isBlank()) {
            sb.append("对话上下文:\n").append(safeContext).append("\n\n");
        }
        if (!attachmentBlock.isBlank()) {
            sb.append("用户附件:\n").append(attachmentBlock).append("\n\n");
        }
        sb.append("用户问题:\n").append(questionOnly);
        return sb.toString();
    }

    /**
     * ReAct 多轮提示词（已修复高风险：硬约束 + 极简）
     */
    public String buildReActUserPrompt(String userPrompt, String context, String provider, String scratchpad, String envMeta) {
        String safePrompt = userPrompt == null ? "" : userPrompt.trim();
        String safeContext = context == null ? "" : context.trim();
        String safeProvider = provider == null ? "auto" : provider.trim();
        String safeScratchpad = scratchpad == null ? "" : scratchpad.trim();
        String safeMeta = envMeta == null ? "" : envMeta.trim();

        String attachmentBlock = extractAttachmentBlock(safePrompt);
        String questionOnly = stripAttachmentTags(safePrompt);

        StringBuilder pb = new StringBuilder();
        pb.append("provider=").append(safeProvider).append("\n\n");

        if (!safeMeta.isBlank()) {
            pb.append("当前环境: ").append(safeMeta).append("\n\n");
        }
        if (!safeContext.isBlank()) {
            pb.append("对话上下文:\n").append(safeContext).append("\n\n");
        }
        if (!attachmentBlock.isBlank()) {
            pb.append("用户附件:\n").append(attachmentBlock).append("\n\n");
        }

        pb.append("用户问题:\n").append(questionOnly);

        if (!safeScratchpad.isBlank()) {
            pb.append("\n\n--- 历史观察结果 ---\n")
              .append(safeScratchpad)
              .append("\n\n只允许输出一个 JSON 对象。")
              .append("请按内部思考框架决策后输出 tool_call 或 final。")
              .append("禁止输出解释或额外文本。");
        } else {
            pb.append("\n\n只允许输出一个 JSON 对象。")
              .append("请按内部思考框架决策后输出 tool_call 或 final。")
              .append("禁止输出其他文本。");
        }

        return pb.toString();
    }

    /**
     * 原生工具系统提示词（已恢复关键策略）
     */
    public String buildNativeToolSystemPrompt(String agentMode, boolean proUser, java.util.List<String> workspaces, java.util.List<MihtnelisAgentStreamService.SkillEntry> skills, boolean snapshotMode) {
        String mode = agentMode == null ? "mihtnelis" : agentMode.trim().toLowerCase();
        return switch (mode) {
            case "r1pxc" -> r1pxcPromptBuilder.buildNativeToolSystemPrompt(proUser, workspaces, skills, snapshotMode);
            case "edoc" -> edocPromptBuilder.buildNativeToolSystemPrompt(proUser, workspaces, skills, snapshotMode);
            default -> mihtnelisPromptBuilder.buildNativeToolSystemPrompt(proUser, workspaces, skills, snapshotMode);
        };
    }

    private static final java.util.regex.Pattern ATTACHMENT_PATTERN =
            java.util.regex.Pattern.compile("<attachment name=\"[^\"]*\">\\n[\\s\\S]*?\\n</attachment>",
                    java.util.regex.Pattern.DOTALL);

    /**
     * 提取用户消息中的所有 attachment 块（保留原始 XML 标签）。
     */
    String extractAttachmentBlock(String message) {
        if (message == null || message.isBlank()) return "";
        java.util.regex.Matcher matcher = ATTACHMENT_PATTERN.matcher(message);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(matcher.group());
        }
        return sb.toString();
    }

    /**
     * 从用户消息中移除所有 attachment 块，仅保留纯文本问题。
     */
    String stripAttachmentTags(String message) {
        if (message == null || message.isBlank()) return "";
        return ATTACHMENT_PATTERN.matcher(message).replaceAll("").trim();
    }

}
