package com.ke.utopia.agent.memory.memory;

import com.ke.utopia.agent.memory.config.MemoryAgentConfig;
import com.ke.utopia.agent.memory.exception.MemoryCapacityExceededException;
import com.ke.utopia.agent.memory.model.*;
import com.ke.utopia.agent.memory.security.SecurityScanner;
import com.ke.utopia.agent.memory.spi.EmbeddingService;
import com.ke.utopia.agent.memory.spi.KeywordSearchService;
import com.ke.utopia.agent.memory.spi.MemoryStorage;
import com.ke.utopia.agent.memory.spi.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * 策划记忆管理器：用户画像 + Agent 笔记。
 * 写入立即持久化但不影响当前会话快照（保护 LLM Prefix Cache）。
 */
public final class CuratedMemoryManager {

    private static final Logger log = LoggerFactory.getLogger(CuratedMemoryManager.class);
    private static final String ENTRY_DELIMITER = "\n§\n";

    private final MemoryStorage storage;
    private final SecurityScanner securityScanner;
    private final int memoryCharLimit;
    private final int userProfileCharLimit;
    private final MemoryAgentConfig config;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final MemoryConflictDetector conflictDetector;
    private final MemoryAgentConfig.ConflictDetectionMode conflictMode;
    private final ExecutorService conflictExecutor;
    private final RelevanceTracker relevanceTracker;
    private final MemoryMetricsCollector metricsCollector;
    private KeywordSearchService keywordSearchService;

    public CuratedMemoryManager(MemoryStorage storage, SecurityScanner securityScanner,
                                 int memoryCharLimit, int userProfileCharLimit) {
        this(storage, securityScanner, memoryCharLimit, userProfileCharLimit, null, null, null, null);
    }

    public CuratedMemoryManager(MemoryStorage storage, SecurityScanner securityScanner,
                                 int memoryCharLimit, int userProfileCharLimit,
                                 MemoryAgentConfig config,
                                 EmbeddingService embeddingService, VectorStore vectorStore,
                                 MemoryConflictDetector conflictDetector) {
        this(storage, securityScanner, memoryCharLimit, userProfileCharLimit,
                config, embeddingService, vectorStore, conflictDetector,
                null, null, null, null);
    }

    public CuratedMemoryManager(MemoryStorage storage, SecurityScanner securityScanner,
                                 int memoryCharLimit, int userProfileCharLimit,
                                 MemoryAgentConfig config,
                                 EmbeddingService embeddingService, VectorStore vectorStore,
                                 MemoryConflictDetector conflictDetector,
                                 ExecutorService conflictExecutor,
                                 RelevanceTracker relevanceTracker,
                                 MemoryMetricsCollector metricsCollector,
                                 KeywordSearchService keywordSearchService) {
        this.storage = storage;
        this.securityScanner = securityScanner;
        this.memoryCharLimit = memoryCharLimit;
        this.userProfileCharLimit = userProfileCharLimit;
        this.config = config;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.conflictDetector = conflictDetector;
        this.conflictMode = config != null ? config.getConflictDetectionMode() : MemoryAgentConfig.ConflictDetectionMode.SYNC;
        this.conflictExecutor = conflictExecutor;
        this.relevanceTracker = relevanceTracker;
        this.metricsCollector = metricsCollector;
        this.keywordSearchService = keywordSearchService;
    }

    /**
     * 捕获冻结快照。会话启动时调用一次，之后不再改变。
     * 只包含 CORE 层记忆。
     */
    public MemorySnapshot captureSnapshot(String userId) {
        List<MemoryEntry> memoryEntries = storage.getMemoryEntries(userId, MemoryType.MEMORY).stream()
                .filter(e -> e.getTier() == MemoryTier.CORE)
                .collect(Collectors.toList());
        List<MemoryEntry> profileEntries = storage.getMemoryEntries(userId, MemoryType.USER_PROFILE).stream()
                .filter(e -> e.getTier() == MemoryTier.CORE)
                .collect(Collectors.toList());

        String memoryBlock = formatEntries(memoryEntries);
        String profileBlock = formatEntries(profileEntries);

        log.debug("Captured memory snapshot for user {}: {} memory entries, {} profile entries",
                userId, memoryEntries.size(), profileEntries.size());
        return new MemorySnapshot(memoryBlock, profileBlock, java.time.Instant.now());
    }

    /**
     * 添加记忆条目。执行安全扫描、去重、冲突检测、容量检查。
     */
    public MemoryEntry addEntry(String userId, String content, MemoryType type) {
        long startNs = System.nanoTime();
        try {
            // 1. Security scan
            securityScanner.scanAndThrow(content);

            // 2. Create entry
            MemoryEntry entry = MemoryEntry.of(content, type);

            // 3. Dedup check
            List<MemoryEntry> existing = storage.getMemoryEntries(userId, type);
            if (existing.stream().anyMatch(e -> e.getContent().equals(content))) {
                log.debug("Duplicate memory entry skipped for user {}", userId);
                return entry;
            }

            // 4. Conflict detection (if enabled)
            if (conflictDetector != null) {
                if (conflictMode == MemoryAgentConfig.ConflictDetectionMode.SYNC) {
                    entry = handleConflictSync(userId, entry, existing);
                    if (entry == null) {
                        return entry; // was discarded
                    }
                } else {
                    // ASYNC: write immediately, fix in background
                    entry = handleConflictAsync(userId, entry, existing);
                }
            }

            // 5. Capacity check (only count CORE tier entries)
            List<MemoryEntry> coreEntries = existing.stream()
                    .filter(e -> e.getTier() == MemoryTier.CORE)
                    .collect(Collectors.toList());
            int currentTotal = coreEntries.stream().mapToInt(e -> e.getContent().length()).sum()
                    + (coreEntries.isEmpty() ? 0 : coreEntries.size() * ENTRY_DELIMITER.length());
            int newTotal = currentTotal + entry.getContent().length() + ENTRY_DELIMITER.length();
            int limit = (type == MemoryType.MEMORY) ? memoryCharLimit : userProfileCharLimit;
            if (newTotal > limit) {
                throw new MemoryCapacityExceededException(newTotal, limit);
            }

            // 6. Persist
            MemoryEntry saved = storage.addMemoryEntry(userId, entry);

            // 7. Vectorize (if semantic search enabled)
            vectorizeEntry(userId, saved, content);

            // 8. Index for keyword search (if hybrid search enabled)
            if (keywordSearchService != null) {
                try {
                    keywordSearchService.index(entry.getId(), content, userId);
                } catch (Exception e) {
                    log.warn("Failed to index entry {} for keyword search: {}", entry.getId(), e.getMessage());
                }
            }

            log.debug("Added {} entry for user {}: {} chars ({}% capacity)",
                    type, userId, content.length(), (newTotal * 100 / limit));
            return saved;
        } finally {
            if (metricsCollector != null) {
                metricsCollector.recordAddMemory(System.nanoTime() - startNs);
            }
        }
    }

    /**
     * 同步冲突检测处理：等待 LLM 检测结果后再写入。
     * 修复了 REPLACE_OLD 和 MERGE 分支的缺失逻辑。
     */
    private MemoryEntry handleConflictSync(String userId, MemoryEntry entry, List<MemoryEntry> existing) {
        ConflictResolution resolution = conflictDetector.detect(entry.getContent(), existing);
        if (metricsCollector != null) {
            metricsCollector.recordConflictDetection(resolution.getResolution().name());
        }
        switch (resolution.getResolution()) {
            case DISCARD_NEW:
                log.debug("New memory discarded due to conflict resolution: {}", resolution.getExplanation());
                return null;
            case REPLACE_OLD:
                log.debug("Conflict detected, replacing old memory: {}", resolution.getExplanation());
                // Find the conflicting entry by replacedEntryId or by content matching
                String targetId = resolution.getReplacedEntryId();
                if (targetId != null) {
                    removeEntry(userId, targetId);
                } else {
                    // Fallback: remove the first conflicting entry
                    existing.stream().findFirst().ifPresent(e -> removeEntry(userId, e.getId()));
                }
                // New entry will be added normally
                break;
            case MERGE:
                if (resolution.getMergedContent() != null) {
                    // Remove old conflicting entry and use merged content
                    String mergeTargetId = resolution.getReplacedEntryId();
                    if (mergeTargetId != null) {
                        removeEntry(userId, mergeTargetId);
                    } else {
                        existing.stream().findFirst().ifPresent(e -> removeEntry(userId, e.getId()));
                    }
                    entry = MemoryEntry.of(resolution.getMergedContent(), entry.getType());
                }
                break;
            case KEEP_BOTH:
                // Normal add
                break;
        }
        return entry;
    }

    /**
     * 异步冲突检测处理：立即写入，后台检测冲突后修正。
     */
    private MemoryEntry handleConflictAsync(String userId, MemoryEntry entry, List<MemoryEntry> existing) {
        if (conflictExecutor == null) {
            log.warn("Async conflict detection requested but no executor provided, falling back to KEEP_BOTH");
            return entry;
        }
        // Save a snapshot of existing entries for the background task
        List<MemoryEntry> existingSnapshot = new ArrayList<>(existing);
        MemoryEntry entrySnapshot = entry;
        conflictExecutor.submit(() -> {
            try {
                ConflictResolution resolution = conflictDetector.detect(entrySnapshot.getContent(), existingSnapshot);
                if (metricsCollector != null) {
                    metricsCollector.recordConflictDetection(resolution.getResolution().name());
                }
                switch (resolution.getResolution()) {
                    case DISCARD_NEW:
                        log.debug("Async conflict: discarding new entry {}", entrySnapshot.getId());
                        removeEntry(userId, entrySnapshot.getId());
                        break;
                    case REPLACE_OLD:
                        log.debug("Async conflict: replacing old memory: {}", resolution.getExplanation());
                        removeEntry(userId, resolution.getReplacedEntryId());
                        break;
                    case MERGE:
                        if (resolution.getMergedContent() != null) {
                            removeEntry(userId, resolution.getReplacedEntryId() != null
                                    ? resolution.getReplacedEntryId()
                                    : existingSnapshot.stream().findFirst().map(MemoryEntry::getId).orElse(null));
                            addEntry(userId, resolution.getMergedContent(), entrySnapshot.getType());
                        }
                        break;
                    case KEEP_BOTH:
                        break;
                }
            } catch (Exception e) {
                log.warn("Async conflict detection failed: {}", e.getMessage());
            }
        });
        return entry;
    }

    public Optional<MemoryEntry> replaceEntry(String userId, String entryId, String newContent) {
        securityScanner.scanAndThrow(newContent);
        Optional<MemoryEntry> result = storage.replaceMemoryEntry(userId, entryId, newContent);
        result.ifPresent(e -> log.debug("Replaced memory entry {} for user {}", entryId, userId));
        return result;
    }

    public boolean removeEntry(String userId, String entryId) {
        boolean removed = storage.removeMemoryEntry(userId, entryId);
        if (removed) {
            // Also remove from vector store
            if (vectorStore != null) {
                try {
                    vectorStore.delete(entryId);
                } catch (Exception e) {
                    log.warn("Failed to delete vector for entry {}: {}", entryId, e.getMessage());
                }
            }
            // Also remove from keyword search index
            if (keywordSearchService != null) {
                try {
                    keywordSearchService.remove(entryId);
                } catch (Exception e) {
                    log.warn("Failed to remove keyword index for entry {}: {}", entryId, e.getMessage());
                }
            }
            // Also remove from relevance tracker
            if (relevanceTracker != null) {
                relevanceTracker.remove(entryId);
            }
            log.debug("Removed memory entry {} for user {}", entryId, userId);
        }
        return removed;
    }

    /**
     * 语义搜索 + 相关性跟踪。
     */
    public List<VectorSearchResult> searchWithRelevanceTracking(String userId, float[] queryEmbedding, int topK) {
        Map<String, String> filter = new HashMap<>();
        filter.put("userId", userId);
        List<VectorSearchResult> results = vectorStore.search(queryEmbedding, topK, filter);

        if (relevanceTracker != null) {
            for (VectorSearchResult r : results) {
                if (relevanceTracker.recordRetrieval(r.getId())) {
                    // Boost importance on every BOOST_INTERVAL-th retrieval
                    Optional<MemoryEntry> entryOpt = storage.getMemoryEntry(userId, r.getId());
                    entryOpt.ifPresent(entry -> {
                        double boostedScore = relevanceTracker.calculateBoostedScore(entry.getImportanceScore());
                        storage.updateMemoryEntry(userId, entry.withImportanceScore(boostedScore));
                        log.debug("Boosted importance for entry {}: {} -> {}", entry.getId(), entry.getImportanceScore(), boostedScore);
                    });
                }
            }
        }
        return results;
    }

    private void vectorizeEntry(String userId, MemoryEntry entry, String content) {
        if (embeddingService != null && vectorStore != null) {
            try {
                float[] embedding = embeddingService.embed(content);
                Map<String, String> metadata = new HashMap<>();
                metadata.put("userId", userId);
                metadata.put("type", entry.getType().name());
                metadata.put("content", content);
                vectorStore.upsert(entry.getId(), embedding, metadata);
            } catch (Exception e) {
                log.warn("Failed to vectorize memory entry {}: {}", entry.getId(), e.getMessage());
            }
        }
    }

    public List<MemoryEntry> getEntries(String userId, MemoryType type) {
        return storage.getMemoryEntries(userId, type);
    }

    public UserProfile getUserProfile(String userId) {
        return storage.getUserProfile(userId);
    }

    /**
     * 更新记忆重要性评分。
     */
    public boolean updateImportanceScore(String userId, String entryId, double score) {
        Optional<MemoryEntry> entryOpt = storage.getMemoryEntry(userId, entryId);
        if (entryOpt.isPresent()) {
            return storage.updateMemoryEntry(userId, entryOpt.get().withImportanceScore(score));
        }
        return false;
    }

    private String formatEntries(List<MemoryEntry> entries) {
        return entries.stream()
                .map(MemoryEntry::getContent)
                .collect(Collectors.joining(ENTRY_DELIMITER));
    }
}
