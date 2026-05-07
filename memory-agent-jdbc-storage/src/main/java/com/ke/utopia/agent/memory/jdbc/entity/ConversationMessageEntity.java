package com.ke.utopia.agent.memory.jdbc.entity;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import java.time.Instant;

@Table(name = "ma_conversation_message")
public class ConversationMessageEntity {

    /**
     * 数据库自增主键。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 业务ID (UUID)。
     */
    @Column(name = "message_id", length = 36)
    private String messageId;

    @Column(name = "session_id", length = 36)
    private String sessionId;

    @Column(length = 32)
    private String role;

    @Column
    private String content;

    @Column(name = "tool_call_id", length = 128)
    private String toolCallId;

    @Column(name = "tool_name", length = 128)
    private String toolName;

    @Column
    private Instant timestamp;

    @Column(name = "token_count")
    private Integer tokenCount;

    @Column
    private String metadata;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getToolCallId() { return toolCallId; }
    public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }

    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public Integer getTokenCount() { return tokenCount; }
    public void setTokenCount(Integer tokenCount) { this.tokenCount = tokenCount; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
}
