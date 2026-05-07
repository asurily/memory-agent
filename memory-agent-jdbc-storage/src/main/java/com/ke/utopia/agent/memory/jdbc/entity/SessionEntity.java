package com.ke.utopia.agent.memory.jdbc.entity;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import java.time.Instant;

@Table(name = "ma_session")
public class SessionEntity {

    /**
     * 数据库自增主键。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 业务ID (UUID)。
     */
    @Column(name = "session_id", length = 36)
    private String sessionId;

    @Column(name = "user_id", length = 128)
    private String userId;

    @Column(length = 64)
    private String source;

    @Column(length = 128)
    private String model;

    @Column(name = "parent_session_id", length = 36)
    private String parentSessionId;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(length = 512)
    private String title;

    @Column(name = "message_count")
    private Integer messageCount;

    @Column(length = 32)
    private String status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getParentSessionId() { return parentSessionId; }
    public void setParentSessionId(String parentSessionId) { this.parentSessionId = parentSessionId; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getEndedAt() { return endedAt; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Integer getMessageCount() { return messageCount; }
    public void setMessageCount(Integer messageCount) { this.messageCount = messageCount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
