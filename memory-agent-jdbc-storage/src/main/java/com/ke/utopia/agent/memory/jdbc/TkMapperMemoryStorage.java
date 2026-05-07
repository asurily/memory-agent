package com.ke.utopia.agent.memory.jdbc;

import com.ke.utopia.agent.memory.exception.SessionNotFoundException;
import com.ke.utopia.agent.memory.jdbc.converter.EntityConverter;
import com.ke.utopia.agent.memory.jdbc.entity.*;
import com.ke.utopia.agent.memory.jdbc.mapper.*;
import com.ke.utopia.agent.memory.model.*;
import com.ke.utopia.agent.memory.spi.MemoryStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 基于 MySQL + TkMapper 的持久化存储实现。
 * 行为与 InMemoryMemoryStorage 完全一致。
 */
public class TkMapperMemoryStorage implements MemoryStorage {

    private static final Logger log = LoggerFactory.getLogger(TkMapperMemoryStorage.class);

    private final SessionMapper sessionMapper;
    private final ConversationMessageMapper messageMapper;
    private final MemoryEntryMapper memoryEntryMapper;
    private final IntentSummaryMapper summaryMapper;

    public TkMapperMemoryStorage(SessionMapper sessionMapper,
                                  ConversationMessageMapper messageMapper,
                                  MemoryEntryMapper memoryEntryMapper,
                                  IntentSummaryMapper summaryMapper) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.memoryEntryMapper = memoryEntryMapper;
        this.summaryMapper = summaryMapper;
    }

    @Override
    public void initialize() {
        log.info("TkMapperMemoryStorage initialized");
    }

    @Override
    public void shutdown() {
        log.info("TkMapperMemoryStorage shut down");
    }

    // --- Session ---

    @Override
    public Session createSession(String userId, String source) {
        Session session = Session.create(userId, source);
        sessionMapper.insertSelective(EntityConverter.toEntity(session));
        return session;
    }

    @Override
    public Optional<Session> getSession(String sessionId) {
        return Optional.ofNullable(sessionMapper.findBySessionId(sessionId))
                .map(EntityConverter::toDomain);
    }

    @Override
    public List<Session> getSessionsByUser(String userId) {
        return sessionMapper.findByUserId(userId).stream()
                .map(EntityConverter::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Session updateSession(Session session) {
        SessionEntity existing = sessionMapper.findBySessionId(session.getId());
        if (existing == null) {
            throw new SessionNotFoundException(session.getId());
        }
        // 设置数据库主键ID，确保更新正确
        SessionEntity entity = EntityConverter.toEntity(session);
        entity.setId(existing.getId());
        sessionMapper.updateByPrimaryKeySelective(entity);
        return session;
    }

    @Override
    public void closeSession(String sessionId) {
        SessionEntity entity = sessionMapper.findBySessionId(sessionId);
        if (entity != null) {
            Session session = EntityConverter.toDomain(entity);
            Session closed = session.close();
            SessionEntity closedEntity = EntityConverter.toEntity(closed);
            closedEntity.setId(entity.getId());
            sessionMapper.updateByPrimaryKeySelective(closedEntity);
        }
    }

    // --- Message ---

    @Override
    public ConversationMessage addMessage(ConversationMessage message) {
        messageMapper.insertSelective(EntityConverter.toEntity(message));
        return message;
    }

    @Override
    public List<ConversationMessage> getMessages(String sessionId) {
        return messageMapper.findBySessionIdOrderByTimestamp(sessionId).stream()
                .map(EntityConverter::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<ConversationMessage> getRecentMessages(String sessionId, int limit) {
        List<ConversationMessageEntity> entities = messageMapper.findRecentBySessionId(sessionId, limit);
        // DB returns DESC order, need to reverse to ASC
        List<ConversationMessage> result = new ArrayList<>();
        for (int i = entities.size() - 1; i >= 0; i--) {
            result.add(EntityConverter.toDomain(entities.get(i)));
        }
        return result;
    }

    @Override
    public int getMessageCount(String sessionId) {
        return messageMapper.countBySessionId(sessionId);
    }

    // --- Memory Entry ---

    @Override
    public MemoryEntry addMemoryEntry(String userId, MemoryEntry entry) {
        // Dedup: same as InMemory behavior
        int count = memoryEntryMapper.countByUserIdAndContent(userId, entry.getContent());
        if (count > 0) {
            return entry;
        }
        memoryEntryMapper.insertSelective(EntityConverter.toEntity(userId, entry));
        return entry;
    }

    @Override
    public Optional<MemoryEntry> replaceMemoryEntry(String userId, String entryId, String newContent) {
        MemoryEntryEntity entity = memoryEntryMapper.findByMemoryIdAndUserId(entryId, userId);
        if (entity == null) {
            return Optional.empty();
        }
        MemoryEntry existing = EntityConverter.toDomain(entity);
        MemoryEntry updated = existing.withContent(newContent);
        MemoryEntryEntity updatedEntity = EntityConverter.toEntity(userId, updated);
        updatedEntity.setId(entity.getId());
        memoryEntryMapper.updateByPrimaryKeySelective(updatedEntity);
        return Optional.of(updated);
    }

    @Override
    public boolean removeMemoryEntry(String userId, String entryId) {
        MemoryEntryEntity entity = memoryEntryMapper.findByMemoryIdAndUserId(entryId, userId);
        if (entity == null) {
            return false;
        }
        return memoryEntryMapper.deleteByPrimaryKey(entity.getId()) > 0;
    }

    @Override
    public List<MemoryEntry> getMemoryEntries(String userId, MemoryType type) {
        return memoryEntryMapper.findByUserIdAndType(userId, type.name()).stream()
                .map(EntityConverter::toDomain)
                .collect(Collectors.toList());
    }

    // --- Tiered Memory ---

    @Override
    public List<MemoryEntry> getMemoryEntriesByTier(String userId, MemoryTier tier) {
        return memoryEntryMapper.findByUserIdAndTier(userId, tier.name()).stream()
                .map(EntityConverter::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public boolean updateMemoryTier(String userId, String entryId, MemoryTier newTier) {
        MemoryEntryEntity entity = memoryEntryMapper.findByMemoryIdAndUserId(entryId, userId);
        if (entity == null) {
            return false;
        }
        entity.setTier(newTier.name());
        return memoryEntryMapper.updateByPrimaryKeySelective(entity) > 0;
    }

    @Override
    public int getMemoryEntryCountByTier(String userId, MemoryTier tier) {
        return memoryEntryMapper.countByUserIdAndTier(userId, tier.name());
    }

    @Override
    public Optional<MemoryEntry> getMemoryEntry(String userId, String entryId) {
        return Optional.ofNullable(memoryEntryMapper.findByMemoryIdAndUserId(entryId, userId))
                .map(EntityConverter::toDomain);
    }

    @Override
    public boolean updateMemoryEntry(String userId, MemoryEntry updatedEntry) {
        MemoryEntryEntity existing = memoryEntryMapper.findByMemoryIdAndUserId(updatedEntry.getId(), userId);
        if (existing == null) {
            return false;
        }
        MemoryEntryEntity updatedEntity = EntityConverter.toEntity(userId, updatedEntry);
        updatedEntity.setId(existing.getId());
        return memoryEntryMapper.updateByPrimaryKeySelective(updatedEntity) > 0;
    }

    @Override
    public List<String> getAllUserIds() {
        return memoryEntryMapper.findAllUserIds();
    }

    // --- User Profile ---

    @Override
    public UserProfile getUserProfile(String userId) {
        List<MemoryEntry> profileEntries = getMemoryEntries(userId, MemoryType.USER_PROFILE);
        List<MemoryEntry> memoryEntries = getMemoryEntries(userId, MemoryType.MEMORY);
        Instant lastUpdated = memoryEntries.stream()
                .map(MemoryEntry::getUpdatedAt)
                .max(Comparator.naturalOrder())
                .orElse(Instant.now());
        return new UserProfile(userId, profileEntries, memoryEntries, lastUpdated, Collections.emptyMap());
    }

    // --- Intent Summary ---

    @Override
    public IntentSummary saveIntentSummary(IntentSummary summary) {
        summaryMapper.insertSelective(EntityConverter.toEntity(summary));
        return summary;
    }

    @Override
    public List<IntentSummary> getIntentSummaries(String sessionId) {
        return summaryMapper.findBySessionId(sessionId).stream()
                .map(EntityConverter::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<IntentSummary> getIntentSummariesByUser(String userId) {
        return summaryMapper.findByUserId(userId).stream()
                .map(EntityConverter::toDomain)
                .collect(Collectors.toList());
    }
}
