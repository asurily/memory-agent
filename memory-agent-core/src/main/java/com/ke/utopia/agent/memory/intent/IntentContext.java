package com.ke.utopia.agent.memory.intent;

import com.ke.utopia.agent.memory.model.ConversationMessage;
import com.ke.utopia.agent.memory.model.IntentSummary;
import com.ke.utopia.agent.memory.model.MemorySnapshot;
import com.ke.utopia.agent.memory.model.UserProfile;

import java.util.List;
import java.util.Objects;

/**
 * 增量意图推导上下文。
 * <p>
 * 作为 {@link IncrementalIntentEngine} 的输入参数，封装推导所需的所有上下文信息。
 */
public final class IntentContext {

    private final List<ConversationMessage> sessionMessages;
    private final MemorySnapshot memorySnapshot;
    private final UserProfile userProfile;
    private final List<IntentSummary> recentSummaries;

    private IntentContext(Builder builder) {
        this.sessionMessages = builder.sessionMessages;
        this.memorySnapshot = builder.memorySnapshot;
        this.userProfile = builder.userProfile;
        this.recentSummaries = builder.recentSummaries;
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<ConversationMessage> getSessionMessages() {
        return sessionMessages;
    }

    public MemorySnapshot getMemorySnapshot() {
        return memorySnapshot;
    }

    public UserProfile getUserProfile() {
        return userProfile;
    }

    public List<IntentSummary> getRecentSummaries() {
        return recentSummaries;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IntentContext)) return false;
        IntentContext that = (IntentContext) o;
        return Objects.equals(sessionMessages, that.sessionMessages)
                && Objects.equals(memorySnapshot, that.memorySnapshot)
                && Objects.equals(userProfile, that.userProfile)
                && Objects.equals(recentSummaries, that.recentSummaries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionMessages, memorySnapshot, userProfile, recentSummaries);
    }

    @Override
    public String toString() {
        return "IntentContext{" +
                "sessionMessages=" + (sessionMessages != null ? sessionMessages.size() : 0) +
                ", memorySnapshot=" + memorySnapshot +
                ", userProfile=" + userProfile +
                ", recentSummaries=" + (recentSummaries != null ? recentSummaries.size() : 0) +
                '}';
    }

    public static class Builder {
        private List<ConversationMessage> sessionMessages;
        private MemorySnapshot memorySnapshot;
        private UserProfile userProfile;
        private List<IntentSummary> recentSummaries;

        public Builder sessionMessages(List<ConversationMessage> sessionMessages) {
            this.sessionMessages = sessionMessages;
            return this;
        }

        public Builder memorySnapshot(MemorySnapshot memorySnapshot) {
            this.memorySnapshot = memorySnapshot;
            return this;
        }

        public Builder userProfile(UserProfile userProfile) {
            this.userProfile = userProfile;
            return this;
        }

        public Builder recentSummaries(List<IntentSummary> recentSummaries) {
            this.recentSummaries = recentSummaries;
            return this;
        }

        public IntentContext build() {
            return new IntentContext(this);
        }
    }
}
