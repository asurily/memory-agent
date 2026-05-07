package com.ke.utopia.agent.memory.jdbc.entity;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import java.time.Instant;

@Table(name = "ma_intent_summary")
public class IntentSummaryEntity {

    /**
     * 数据库自增主键。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 业务ID (UUID)。
     */
    @Column(name = "summary_id", length = 36)
    private String summaryId;

    @Column(name = "session_id", length = 36)
    private String sessionId;

    @Column(name = "user_id", length = 128)
    private String userId;

    @Column(name = "core_intent")
    private String coreIntent;

    @Column
    private String keyTopics;

    @Column
    private String actionItems;

    @Column(name = "emotional_tone", length = 64)
    private String emotionalTone;

    @Column(name = "full_summary")
    private String fullSummary;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "source_message_count")
    private Integer sourceMessageCount;

    @Column(name = "total_tokens_used")
    private Long totalTokensUsed;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSummaryId() { return summaryId; }
    public void setSummaryId(String summaryId) { this.summaryId = summaryId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getCoreIntent() { return coreIntent; }
    public void setCoreIntent(String coreIntent) { this.coreIntent = coreIntent; }

    public String getKeyTopics() { return keyTopics; }
    public void setKeyTopics(String keyTopics) { this.keyTopics = keyTopics; }

    public String getActionItems() { return actionItems; }
    public void setActionItems(String actionItems) { this.actionItems = actionItems; }

    public String getEmotionalTone() { return emotionalTone; }
    public void setEmotionalTone(String emotionalTone) { this.emotionalTone = emotionalTone; }

    public String getFullSummary() { return fullSummary; }
    public void setFullSummary(String fullSummary) { this.fullSummary = fullSummary; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Integer getSourceMessageCount() { return sourceMessageCount; }
    public void setSourceMessageCount(Integer sourceMessageCount) { this.sourceMessageCount = sourceMessageCount; }

    public Long getTotalTokensUsed() { return totalTokensUsed; }
    public void setTotalTokensUsed(Long totalTokensUsed) { this.totalTokensUsed = totalTokensUsed; }
}
