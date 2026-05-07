package com.ke.utopia.agent.memory.spi;

import com.ke.utopia.agent.memory.model.ConversationMessage;

import java.util.List;

/**
 * 上下文压缩 prompt 的上下文参数。
 */
public final class CompressPromptContext {

    private final List<ConversationMessage> messages;
    private final int targetTokenBudget;

    public CompressPromptContext(List<ConversationMessage> messages, int targetTokenBudget) {
        this.messages = messages;
        this.targetTokenBudget = targetTokenBudget;
    }

    public List<ConversationMessage> getMessages() { return messages; }
    public int getTargetTokenBudget() { return targetTokenBudget; }
}
