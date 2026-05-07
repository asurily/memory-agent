package com.ke.utopia.agent.memory.spi;

import com.ke.utopia.agent.memory.model.ConversationMessage;
import com.ke.utopia.agent.memory.model.IntentSummary;
import com.ke.utopia.agent.memory.model.UserProfile;

import java.util.List;
import java.util.Optional;

/**
 * 意图总结 prompt 的上下文参数。
 */
public final class SummarizePromptContext {

    private final String userId;
    private final String sessionId;
    private final List<ConversationMessage> messages;
    private final Optional<UserProfile> userProfile;
    private final Optional<List<IntentSummary>> previousSummaries;

    public SummarizePromptContext(String userId, String sessionId,
                                   List<ConversationMessage> messages,
                                   Optional<UserProfile> userProfile,
                                   Optional<List<IntentSummary>> previousSummaries) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.messages = messages;
        this.userProfile = userProfile;
        this.previousSummaries = previousSummaries;
    }

    public String getUserId() { return userId; }
    public String getSessionId() { return sessionId; }
    public List<ConversationMessage> getMessages() { return messages; }
    public Optional<UserProfile> getUserProfile() { return userProfile; }
    public Optional<List<IntentSummary>> getPreviousSummaries() { return previousSummaries; }
}
