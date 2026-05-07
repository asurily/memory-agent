package com.ke.utopia.agent.memory.spi;

import com.ke.utopia.agent.memory.model.ConversationMessage;
import com.ke.utopia.agent.memory.model.UserProfile;

import java.util.List;
import java.util.Optional;

/**
 * 记忆提取 prompt 的上下文参数。
 */
public final class MemoryExtractionPromptContext {

    private final List<ConversationMessage> messages;
    private final Optional<UserProfile> existingProfile;

    public MemoryExtractionPromptContext(List<ConversationMessage> messages,
                                          Optional<UserProfile> existingProfile) {
        this.messages = messages;
        this.existingProfile = existingProfile;
    }

    public List<ConversationMessage> getMessages() { return messages; }
    public Optional<UserProfile> getExistingProfile() { return existingProfile; }
}
