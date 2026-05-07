package com.ke.utopia.agent.memory.memory;

import com.ke.utopia.agent.memory.model.*;
import com.ke.utopia.agent.memory.spi.EmbeddingService;
import com.ke.utopia.agent.memory.spi.MemoryStorage;
import com.ke.utopia.agent.memory.spi.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理记忆在层级间的迁移。
 * CORE (L1) ↔ ARCHIVED (L2) ↔ RAW (L3)
 */
public final class MemoryTierManager {

    private static final Logger log = LoggerFactory.getLogger(MemoryTierManager.class);

    private final MemoryStorage storage;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;

    public MemoryTierManager(MemoryStorage storage, EmbeddingService embeddingService, VectorStore vectorStore) {
        this.storage = storage;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
    }

    /**
     * 将记忆从 ARCHIVED 提升到 CORE。
     */
    public boolean promoteToCore(String userId, String entryId) {
        Optional<MemoryEntry> entryOpt = storage.getMemoryEntry(userId, entryId);
        if (entryOpt.isEmpty()) {
            log.warn("Memory entry {} not found for user {}", entryId, userId);
            return false;
        }

        MemoryEntry entry = entryOpt.get();
        if (entry.getTier() == MemoryTier.CORE) {
            log.debug("Entry {} is already CORE tier", entryId);
            return true;
        }

        boolean updated = storage.updateMemoryTier(userId, entryId, MemoryTier.CORE);
        if (updated) {
            log.info("Promoted memory {} to CORE for user {}", entryId, userId);
        }
        return updated;
    }

    /**
     * 将记忆从 CORE 降级到 ARCHIVED。
     */
    public boolean archiveEntry(String userId, String entryId) {
        Optional<MemoryEntry> entryOpt = storage.getMemoryEntry(userId, entryId);
        if (entryOpt.isEmpty()) {
            log.warn("Memory entry {} not found for user {}", entryId, userId);
            return false;
        }

        MemoryEntry entry = entryOpt.get();
        if (entry.getTier() == MemoryTier.ARCHIVED) {
            log.debug("Entry {} is already ARCHIVED tier", entryId);
            return true;
        }

        boolean updated = storage.updateMemoryTier(userId, entryId, MemoryTier.ARCHIVED);
        if (updated) {
            log.info("Archived memory {} for user {}", entryId, userId);
        }
        return updated;
    }

    /**
     * 获取所有 CORE 层记忆。
     */
    public List<MemoryEntry> getCoreMemories(String userId) {
        return storage.getMemoryEntriesByTier(userId, MemoryTier.CORE);
    }

    /**
     * 获取 ARCHIVED 层记忆。
     */
    public List<MemoryEntry> getArchivedMemories(String userId, int limit) {
        List<MemoryEntry> archived = storage.getMemoryEntriesByTier(userId, MemoryTier.ARCHIVED);
        if (archived.size() <= limit) return archived;
        return archived.subList(0, limit);
    }

    /**
     * 语义搜索 ARCHIVED 层记忆。
     */
    public List<VectorSearchResult> searchArchivedMemories(String userId, String query, int topK) {
        if (embeddingService == null || vectorStore == null) {
            log.warn("Semantic search not available: embeddingService or vectorStore not configured");
            return Collections.emptyList();
        }

        float[] queryEmbedding = embeddingService.embed(query);
        Map<String, String> filter = new HashMap<>();
        filter.put("userId", userId);

        List<VectorSearchResult> results = vectorStore.search(queryEmbedding, topK * 2, filter);

        // Filter to only ARCHIVED tier entries
        Set<String> archivedIds = storage.getMemoryEntriesByTier(userId, MemoryTier.ARCHIVED).stream()
                .map(MemoryEntry::getId)
                .collect(Collectors.toSet());

        return results.stream()
                .filter(r -> archivedIds.contains(r.getId()))
                .limit(topK)
                .collect(Collectors.toList());
    }
}
