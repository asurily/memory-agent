package com.ke.utopia.agent.memory.compression;

import com.ke.utopia.agent.memory.model.ConversationMessage;
import com.ke.utopia.agent.memory.model.MessageRole;
import com.ke.utopia.agent.memory.spi.IntentSummarizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 上下文压缩器。三阶段管线：
 * 1. 工具输出裁剪（无 LLM 调用）
 * 2. 头部 + 尾部保护
 * 3. 中间部分 LLM 摘要
 */
public final class ContextCompressor {

    private static final Logger log = LoggerFactory.getLogger(ContextCompressor.class);

    private final IntentSummarizer summarizer;
    private final CompressionConfig config;
    private int ineffectiveCount = 0;

    public ContextCompressor(IntentSummarizer summarizer, CompressionConfig config) {
        this.summarizer = summarizer;
        this.config = config;
    }

    /**
     * 判断是否需要压缩。
     */
    public boolean shouldCompress(List<ConversationMessage> messages, int contextWindowTokens) {
        if (ineffectiveCount >= config.getIneffectiveThreshold()) {
            return false;
        }
        int estimatedTokens = estimateTokens(messages);
        return estimatedTokens > contextWindowTokens * config.getCompressionThresholdRatio();
    }

    /**
     * 执行三阶段压缩。
     */
    public CompressionResult compress(List<ConversationMessage> messages, int contextWindowTokens) {
        int originalTokens = estimateTokens(messages);

        // Phase 1: Prune old tool outputs
        List<ConversationMessage> pruned = pruneToolOutputs(messages);
        log.debug("Phase 1 (tool pruning): {} -> {} messages", messages.size(), pruned.size());

        // Phase 2: Segment into head/middle/tail
        int headCount = Math.min(config.getProtectFirstN(), pruned.size());
        int tailTokenBudget = (int) (contextWindowTokens * config.getTailTokenBudgetRatio());

        // Calculate tail messages
        List<ConversationMessage> tailMessages = new ArrayList<>();
        int tailTokens = 0;
        for (int i = pruned.size() - 1; i >= headCount; i--) {
            ConversationMessage m = pruned.get(i);
            int msgTokens = estimateTokens(m);
            if (tailTokens + msgTokens > tailTokenBudget && !tailMessages.isEmpty()) {
                break;
            }
            tailMessages.add(0, m);
            tailTokens += msgTokens;
        }

        List<ConversationMessage> headMessages = pruned.subList(0, headCount);
        List<ConversationMessage> middleMessages = pruned.subList(
                headCount, pruned.size() - tailMessages.size());

        log.debug("Phase 2 (segmentation): head={}, middle={}, tail={}",
                headMessages.size(), middleMessages.size(), tailMessages.size());

        // Phase 3: LLM summarize middle
        String middleSummary;
        if (middleMessages.isEmpty()) {
            middleSummary = "";
        } else {
            int targetTokens = contextWindowTokens - estimateTokens(headMessages) - tailTokens;
            middleSummary = summarizer.compressConversation(middleMessages, Math.max(targetTokens, 500));
            log.debug("Phase 3 (LLM summary): {} messages compressed to {} chars",
                    middleMessages.size(), middleSummary.length());
        }

        int compressedTokens = estimateTokens(headMessages) +
                middleSummary.length() / 2 + tailTokens;

        // Track ineffective compressions
        double savings = 1.0 - ((double) compressedTokens / originalTokens);
        if (savings < 0.1) {
            ineffectiveCount++;
            log.debug("Ineffective compression detected ({}), count={}",
                    String.format("%.1f%%", savings * 100), ineffectiveCount);
        } else {
            ineffectiveCount = 0;
        }

        return new CompressionResult(
                new ArrayList<>(headMessages), middleSummary,
                tailMessages, originalTokens, compressedTokens);
    }

    /**
     * Phase 1: 裁剪旧的工具输出。
     * 保留最近的工具消息，旧的替换为一行摘要。
     */
    private List<ConversationMessage> pruneToolOutputs(List<ConversationMessage> messages) {
        List<ConversationMessage> result = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            ConversationMessage m = messages.get(i);
            if (m.getRole() == MessageRole.TOOL && m.getContent() != null) {
                // 保留最近 3 条工具消息原文，旧的截断
                long recentToolCount = messages.subList(i, messages.size()).stream()
                        .filter(msg -> msg.getRole() == MessageRole.TOOL)
                        .count();
                if (recentToolCount <= 3) {
                    result.add(m);
                } else {
                    // 创建摘要版本
                    String summary = summarizeToolOutput(m);
                    result.add(ConversationMessage.toolMessage(
                            m.getSessionId(), summary, m.getToolCallId(), m.getToolName()));
                }
            } else {
                result.add(m);
            }
        }
        return result;
    }

    private String summarizeToolOutput(ConversationMessage msg) {
        String content = msg.getContent();
        int lines = content.split("\n").length;
        String toolName = msg.getToolName() != null ? msg.getToolName() : "tool";
        if (content.length() > 100) {
            return "[" + toolName + "] " + content.length() + " chars, " + lines + " lines output (truncated)";
        }
        return "[" + toolName + "] " + content;
    }

    private int estimateTokens(List<ConversationMessage> messages) {
        return messages.stream().mapToInt(this::estimateTokens).sum();
    }

    private int estimateTokens(ConversationMessage msg) {
        // 粗略估算：中文约 1.5 字符/token，英文约 4 字符/token
        return msg.getContent() != null ? msg.getContent().length() / 2 : 0;
    }
}
