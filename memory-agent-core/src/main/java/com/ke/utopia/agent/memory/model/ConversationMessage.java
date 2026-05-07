package com.ke.utopia.agent.memory.model;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 单条对话消息。
 */
public final class ConversationMessage {

    private final String id;
    private final String sessionId;
    private final MessageRole role;
    private final String content;
    private final String toolCallId;
    private final String toolName;
    private final Instant timestamp;
    private final int tokenCount;
    private final Map<String, String> metadata;

    private ConversationMessage(Builder builder) {
        this.id = builder.id;
        this.sessionId = builder.sessionId;
        this.role = builder.role;
        this.content = builder.content;
        this.toolCallId = builder.toolCallId;
        this.toolName = builder.toolName;
        this.timestamp = builder.timestamp;
        this.tokenCount = builder.tokenCount;
        this.metadata = Collections.unmodifiableMap(new HashMap<>(builder.metadata));
    }

    public static ConversationMessage userMessage(String sessionId, String content) {
        return new Builder().sessionId(sessionId).role(MessageRole.USER).content(content).build();
    }

    public static ConversationMessage assistantMessage(String sessionId, String content) {
        return new Builder().sessionId(sessionId).role(MessageRole.ASSISTANT).content(content).build();
    }

    public static ConversationMessage systemMessage(String sessionId, String content) {
        return new Builder().sessionId(sessionId).role(MessageRole.SYSTEM).content(content).build();
    }

    public static ConversationMessage toolMessage(String sessionId, String content,
                                                   String toolCallId, String toolName) {
        return new Builder().sessionId(sessionId).role(MessageRole.TOOL).content(content)
                .toolCallId(toolCallId).toolName(toolName).build();
    }

    public String getId() { return id; }
    public String getSessionId() { return sessionId; }
    public MessageRole getRole() { return role; }
    public String getContent() { return content; }
    public String getToolCallId() { return toolCallId; }
    public String getToolName() { return toolName; }
    public Instant getTimestamp() { return timestamp; }
    public int getTokenCount() { return tokenCount; }
    public Map<String, String> getMetadata() { return metadata; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConversationMessage)) return false;
        ConversationMessage that = (ConversationMessage) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ConversationMessage{id='" + id + "', sessionId='" + sessionId +
                "', role=" + role + ", content='" +
                (content != null && content.length() > 50 ? content.substring(0, 50) + "..." : content) + "'}";
    }

    public static class Builder {
        private String id = UUID.randomUUID().toString();
        private String sessionId;
        private MessageRole role;
        private String content;
        private String toolCallId;
        private String toolName;
        private Instant timestamp = Instant.now();
        private int tokenCount;
        private Map<String, String> metadata = new HashMap<>();

        public Builder id(String id) { this.id = id; return this; }
        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public Builder role(MessageRole role) { this.role = role; return this; }
        public Builder content(String content) { this.content = content; return this; }
        public Builder toolCallId(String toolCallId) { this.toolCallId = toolCallId; return this; }
        public Builder toolName(String toolName) { this.toolName = toolName; return this; }
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
        public Builder tokenCount(int tokenCount) { this.tokenCount = tokenCount; return this; }
        public Builder metadata(Map<String, String> metadata) { this.metadata = metadata; return this; }
        public Builder addMetadata(String key, String value) { this.metadata.put(key, value); return this; }

        public ConversationMessage build() {
            Objects.requireNonNull(sessionId, "sessionId must not be null");
            Objects.requireNonNull(role, "role must not be null");
            Objects.requireNonNull(content, "content must not be null");
            return new ConversationMessage(this);
        }
    }
}
