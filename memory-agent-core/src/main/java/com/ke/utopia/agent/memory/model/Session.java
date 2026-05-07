package com.ke.utopia.agent.memory.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 会话实体。
 */
public final class Session {

    private final String id;
    private final String userId;
    private final String source;
    private final String model;
    private final String parentSessionId;
    private final Instant startedAt;
    private final Instant endedAt;
    private final String title;
    private final int messageCount;
    private final SessionStatus status;

    private Session(Builder builder) {
        this.id = builder.id;
        this.userId = builder.userId;
        this.source = builder.source;
        this.model = builder.model;
        this.parentSessionId = builder.parentSessionId;
        this.startedAt = builder.startedAt;
        this.endedAt = builder.endedAt;
        this.title = builder.title;
        this.messageCount = builder.messageCount;
        this.status = builder.status;
    }

    public static Session create(String userId, String source) {
        return new Builder().userId(userId).source(source).build();
    }

    public Session close() {
        return new Builder().from(this).endedAt(Instant.now()).status(SessionStatus.CLOSED).build();
    }

    public Session withTitle(String title) {
        return new Builder().from(this).title(title).build();
    }

    public Session incrementMessageCount() {
        return new Builder().from(this).messageCount(this.messageCount + 1).build();
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getSource() { return source; }
    public String getModel() { return model; }
    public String getParentSessionId() { return parentSessionId; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public String getTitle() { return title; }
    public int getMessageCount() { return messageCount; }
    public SessionStatus getStatus() { return status; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Session)) return false;
        Session session = (Session) o;
        return Objects.equals(id, session.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Session{id='" + id + "', userId='" + userId + "', status=" + status +
                ", messageCount=" + messageCount + "}";
    }

    public static class Builder {
        private String id = UUID.randomUUID().toString();
        private String userId;
        private String source;
        private String model;
        private String parentSessionId;
        private Instant startedAt = Instant.now();
        private Instant endedAt;
        private String title;
        private int messageCount;
        private SessionStatus status = SessionStatus.ACTIVE;

        public Builder from(Session session) {
            this.id = session.id;
            this.userId = session.userId;
            this.source = session.source;
            this.model = session.model;
            this.parentSessionId = session.parentSessionId;
            this.startedAt = session.startedAt;
            this.endedAt = session.endedAt;
            this.title = session.title;
            this.messageCount = session.messageCount;
            this.status = session.status;
            return this;
        }

        public Builder id(String id) { this.id = id; return this; }
        public Builder userId(String userId) { this.userId = userId; return this; }
        public Builder source(String source) { this.source = source; return this; }
        public Builder model(String model) { this.model = model; return this; }
        public Builder parentSessionId(String parentSessionId) { this.parentSessionId = parentSessionId; return this; }
        public Builder startedAt(Instant startedAt) { this.startedAt = startedAt; return this; }
        public Builder endedAt(Instant endedAt) { this.endedAt = endedAt; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder messageCount(int messageCount) { this.messageCount = messageCount; return this; }
        public Builder status(SessionStatus status) { this.status = status; return this; }

        public Session build() {
            Objects.requireNonNull(userId, "userId must not be null");
            return new Session(this);
        }
    }
}
