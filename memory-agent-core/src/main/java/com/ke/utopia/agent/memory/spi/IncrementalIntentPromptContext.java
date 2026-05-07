package com.ke.utopia.agent.memory.spi;

import com.ke.utopia.agent.memory.model.ConversationMessage;
import com.ke.utopia.agent.memory.model.IncrementalIntent;
import com.ke.utopia.agent.memory.model.MemorySnapshot;

import java.util.List;

/**
 * 增量意图提取 prompt 的上下文参数。
 */
public final class IncrementalIntentPromptContext {

    private final ConversationMessage currentMessage;
    private final List<ConversationMessage> recentMessages;
    private final IncrementalIntent previousIntent;
    private final MemorySnapshot memorySnapshot;

    public IncrementalIntentPromptContext(ConversationMessage currentMessage,
                                           List<ConversationMessage> recentMessages,
                                           IncrementalIntent previousIntent,
                                           MemorySnapshot memorySnapshot) {
        this.currentMessage = currentMessage;
        this.recentMessages = recentMessages;
        this.previousIntent = previousIntent;
        this.memorySnapshot = memorySnapshot;
    }

    public ConversationMessage getCurrentMessage() { return currentMessage; }
    public List<ConversationMessage> getRecentMessages() { return recentMessages; }
    public IncrementalIntent getPreviousIntent() { return previousIntent; }
    public MemorySnapshot getMemorySnapshot() { return memorySnapshot; }
}
