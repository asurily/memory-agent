package com.ke.utopia.agent.memory.jdbc.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ke.utopia.agent.memory.jdbc.entity.*;
import com.ke.utopia.agent.memory.model.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 领域模型 ↔ Entity 转换器。
 * JSON 列（List<String>、Map<String,String>）在 Entity 中存储为 String，
 * 转换时由 Jackson 进行序列化/反序列化。
 */
public final class EntityConverter {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {};

    private EntityConverter() {}

    // --- Session ---

    public static SessionEntity toEntity(Session session) {
        SessionEntity e = new SessionEntity();
        e.setSessionId(session.getId()); // 业务ID
        e.setUserId(session.getUserId());
        e.setSource(session.getSource());
        e.setModel(session.getModel());
        e.setParentSessionId(session.getParentSessionId());
        e.setStartedAt(session.getStartedAt());
        e.setEndedAt(session.getEndedAt());
        e.setTitle(session.getTitle());
        e.setMessageCount(session.getMessageCount());
        e.setStatus(session.getStatus().name());
        return e;
    }

    public static Session toDomain(SessionEntity e) {
        return new Session.Builder()
                .id(e.getSessionId()) // 使用业务ID
                .userId(e.getUserId())
                .source(e.getSource())
                .model(e.getModel())
                .parentSessionId(e.getParentSessionId())
                .startedAt(e.getStartedAt())
                .endedAt(e.getEndedAt())
                .title(e.getTitle())
                .messageCount(e.getMessageCount() != null ? e.getMessageCount() : 0)
                .status(SessionStatus.valueOf(e.getStatus()))
                .build();
    }

    // --- ConversationMessage ---

    public static ConversationMessageEntity toEntity(ConversationMessage msg) {
        ConversationMessageEntity e = new ConversationMessageEntity();
        e.setMessageId(msg.getId()); // 业务ID
        e.setSessionId(msg.getSessionId());
        e.setRole(msg.getRole().name());
        e.setContent(msg.getContent());
        e.setToolCallId(msg.getToolCallId());
        e.setToolName(msg.getToolName());
        e.setTimestamp(msg.getTimestamp());
        e.setTokenCount(msg.getTokenCount());
        e.setMetadata(toJson(msg.getMetadata()));
        return e;
    }

    public static ConversationMessage toDomain(ConversationMessageEntity e) {
        return new ConversationMessage.Builder()
                .id(e.getMessageId()) // 使用业务ID
                .sessionId(e.getSessionId())
                .role(MessageRole.valueOf(e.getRole()))
                .content(e.getContent())
                .toolCallId(e.getToolCallId())
                .toolName(e.getToolName())
                .timestamp(e.getTimestamp())
                .tokenCount(e.getTokenCount() != null ? e.getTokenCount() : 0)
                .metadata(parseMap(e.getMetadata()))
                .build();
    }

    // --- MemoryEntry ---

    public static MemoryEntryEntity toEntity(String userId, MemoryEntry entry) {
        MemoryEntryEntity e = new MemoryEntryEntity();
        e.setMemoryId(entry.getId()); // 业务ID
        e.setUserId(userId);
        e.setContent(entry.getContent());
        e.setType(entry.getType().name());
        e.setCreatedAt(entry.getCreatedAt());
        e.setUpdatedAt(entry.getUpdatedAt());
        e.setLastAccessedAt(entry.getLastAccessedAt());
        e.setAccessCount(entry.getAccessCount());
        e.setImportanceScore(entry.getImportanceScore());
        e.setTier(entry.getTier().name());
        return e;
    }

    public static MemoryEntry toDomain(MemoryEntryEntity e) {
        return MemoryEntry.reconstruct(
                e.getMemoryId(), // 使用业务ID
                e.getContent(),
                MemoryType.valueOf(e.getType()),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getLastAccessedAt(),
                e.getAccessCount() != null ? e.getAccessCount() : 0,
                e.getImportanceScore() != null ? e.getImportanceScore() : 0.5,
                MemoryTier.valueOf(e.getTier())
        );
    }

    // --- IntentSummary ---

    public static IntentSummaryEntity toEntity(IntentSummary summary) {
        IntentSummaryEntity e = new IntentSummaryEntity();
        e.setSummaryId(summary.getId()); // 业务ID
        e.setSessionId(summary.getSessionId());
        e.setUserId(summary.getUserId());
        e.setCoreIntent(summary.getCoreIntent());
        e.setKeyTopics(toJson(summary.getKeyTopics()));
        e.setActionItems(toJson(summary.getActionItems()));
        e.setEmotionalTone(summary.getEmotionalTone());
        e.setFullSummary(summary.getFullSummary());
        e.setCreatedAt(summary.getCreatedAt());
        e.setSourceMessageCount(summary.getSourceMessageCount());
        e.setTotalTokensUsed(summary.getTotalTokensUsed());
        return e;
    }

    public static IntentSummary toDomain(IntentSummaryEntity e) {
        return IntentSummary.builder()
                .id(e.getSummaryId()) // 使用业务ID
                .sessionId(e.getSessionId())
                .userId(e.getUserId())
                .coreIntent(e.getCoreIntent())
                .keyTopics(parseList(e.getKeyTopics()))
                .actionItems(parseList(e.getActionItems()))
                .emotionalTone(e.getEmotionalTone())
                .fullSummary(e.getFullSummary())
                .createdAt(e.getCreatedAt())
                .sourceMessageCount(e.getSourceMessageCount() != null ? e.getSourceMessageCount() : 0)
                .totalTokensUsed(e.getTotalTokensUsed() != null ? e.getTotalTokensUsed() : 0L)
                .build();
    }

    // --- JSON helpers ---

    private static String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return JSON_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }

    private static List<String> parseList(String json) {
        if (json == null || json.isEmpty()) return Collections.emptyList();
        try {
            return JSON_MAPPER.readValue(json, LIST_TYPE);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize JSON to List<String>", e);
        }
    }

    private static Map<String, String> parseMap(String json) {
        if (json == null || json.isEmpty()) return Collections.emptyMap();
        try {
            return JSON_MAPPER.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize JSON to Map<String,String>", e);
        }
    }
}
