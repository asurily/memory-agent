package com.ke.utopia.agent.memory.model;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * LLM 意图总结结果。
 */
public final class IntentSummary {

    private final String id;
    private final String sessionId;
    private final String userId;
    private final String coreIntent;
    private final List<String> keyTopics;
    private final List<String> actionItems;
    private final String emotionalTone;
    private final String fullSummary;
    private final Instant createdAt;
    private final int sourceMessageCount;
    private final long totalTokensUsed;

    private IntentSummary(Builder builder) {
        this.id = builder.id;
        this.sessionId = builder.sessionId;
        this.userId = builder.userId;
        this.coreIntent = builder.coreIntent;
        this.keyTopics = Collections.unmodifiableList(builder.keyTopics);
        this.actionItems = Collections.unmodifiableList(builder.actionItems);
        this.emotionalTone = builder.emotionalTone;
        this.fullSummary = builder.fullSummary;
        this.createdAt = builder.createdAt;
        this.sourceMessageCount = builder.sourceMessageCount;
        this.totalTokensUsed = builder.totalTokensUsed;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getId() { return id; }
    public String getSessionId() { return sessionId; }
    public String getUserId() { return userId; }
    public String getCoreIntent() { return coreIntent; }
    public List<String> getKeyTopics() { return keyTopics; }
    public List<String> getActionItems() { return actionItems; }
    public String getEmotionalTone() { return emotionalTone; }
    public String getFullSummary() { return fullSummary; }
    public Instant getCreatedAt() { return createdAt; }
    public int getSourceMessageCount() { return sourceMessageCount; }
    public long getTotalTokensUsed() { return totalTokensUsed; }

    @Override
    public String toString() {
        return "IntentSummary{id='" + id + "', coreIntent='" + coreIntent +
                "', topics=" + keyTopics + ", tone='" + emotionalTone + "'}";
    }

    public static class Builder {
        private String id = UUID.randomUUID().toString();
        private String sessionId;
        private String userId;
        private String coreIntent;
        private List<String> keyTopics = Collections.emptyList();
        private List<String> actionItems = Collections.emptyList();
        private String emotionalTone;
        private String fullSummary;
        private Instant createdAt = Instant.now();
        private int sourceMessageCount;
        private long totalTokensUsed;

        public Builder id(String id) { this.id = id; return this; }
        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder coreIntent(String coreIntent) { this.coreIntent = coreIntent; return this; }
        public Builder keyTopics(List<String> keyTopics) { this.keyTopics = keyTopics; return this; }
        public Builder actionItems(List<String> actionItems) { this.actionItems = actionItems; return this; }
        public Builder emotionalTone(String emotionalTone) { this.emotionalTone = emotionalTone; return this; }
        public Builder fullSummary(String fullSummary) { this.fullSummary = fullSummary; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder sourceMessageCount(int sourceMessageCount) { this.sourceMessageCount = sourceMessageCount; return this; }
        public Builder totalTokensUsed(long totalTokensUsed) { this.totalTokensUsed = totalTokensUsed; return this; }

        public IntentSummary build() {
            Objects.requireNonNull(sessionId, "sessionId must not be null");
            Objects.requireNonNull(userId, "userId must not be null");
            return new IntentSummary(this);
        }
    }
}
