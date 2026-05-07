package com.ke.utopia.agent.memory.spi;

import com.ke.utopia.agent.memory.model.MemoryEntry;
import com.ke.utopia.agent.memory.model.MemoryTier;
import com.ke.utopia.agent.memory.model.MemoryType;
import com.ke.utopia.agent.memory.model.*;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 可插拔存储后端 SPI。
 * 实现：InMemory（默认）、MySQL、Redis 等。
 */
public interface MemoryStorage {

    // --- Session ---

    Session createSession(String userId, String source);

    Optional<Session> getSession(String sessionId);

    List<Session> getSessionsByUser(String userId);

    Session updateSession(Session session);

    void closeSession(String sessionId);

    // --- Message ---

    ConversationMessage addMessage(ConversationMessage message);

    List<ConversationMessage> getMessages(String sessionId);

    List<ConversationMessage> getRecentMessages(String sessionId, int limit);

    int getMessageCount(String sessionId);

    // --- Memory Entry ---

    MemoryEntry addMemoryEntry(String userId, MemoryEntry entry);

    Optional<MemoryEntry> replaceMemoryEntry(String userId, String entryId, String newContent);

    boolean removeMemoryEntry(String userId, String entryId);

    List<MemoryEntry> getMemoryEntries(String userId, MemoryType type);

    // --- Tiered Memory ---

    List<MemoryEntry> getMemoryEntriesByTier(String userId, MemoryTier tier);

    boolean updateMemoryTier(String userId, String entryId, MemoryTier newTier);

    int getMemoryEntryCountByTier(String userId, MemoryTier tier);

    Optional<MemoryEntry> getMemoryEntry(String userId, String entryId);

    boolean updateMemoryEntry(String userId, MemoryEntry updatedEntry);

    // --- User Profile ---

    UserProfile getUserProfile(String userId);

    // --- Intent Summary ---

    IntentSummary saveIntentSummary(IntentSummary summary);

    List<IntentSummary> getIntentSummaries(String sessionId);

    List<IntentSummary> getIntentSummariesByUser(String userId);

    // --- User Management ---

    /**
     * 获取所有有记忆条目的用户 ID。
     * 用于衰退引擎遍历所有用户。
     */
    default List<String> getAllUserIds() {
        return Collections.emptyList();
    }

    // --- Lifecycle ---

    void initialize();

    void shutdown();
}
