package com.ke.utopia.agent.memory.compression;

import com.ke.utopia.agent.memory.model.ConversationMessage;

import java.util.List;

/**
 * 压缩结果：保留头部、压缩中间为摘要文本、保留尾部。
 */
public final class CompressionResult {

    private final List<ConversationMessage> headMessages;
    private final String middleSummary;
    private final List<ConversationMessage> tailMessages;
    private final int originalTokenCount;
    private final int compressedTokenCount;

    public CompressionResult(List<ConversationMessage> headMessages, String middleSummary,
                              List<ConversationMessage> tailMessages,
                              int originalTokenCount, int compressedTokenCount) {
        this.headMessages = headMessages;
        this.middleSummary = middleSummary;
        this.tailMessages = tailMessages;
        this.originalTokenCount = originalTokenCount;
        this.compressedTokenCount = compressedTokenCount;
    }

    public double savingsRatio() {
        if (originalTokenCount == 0) return 0;
        return 1.0 - ((double) compressedTokenCount / originalTokenCount);
    }

    public List<ConversationMessage> getHeadMessages() { return headMessages; }
    public String getMiddleSummary() { return middleSummary; }
    public List<ConversationMessage> getTailMessages() { return tailMessages; }
    public int getOriginalTokenCount() { return originalTokenCount; }
    public int getCompressedTokenCount() { return compressedTokenCount; }

    @Override
    public String toString() {
        return "CompressionResult{savings=" + String.format("%.1f%%", savingsRatio() * 100) +
                ", head=" + headMessages.size() + ", tail=" + tailMessages.size() + "}";
    }
}
