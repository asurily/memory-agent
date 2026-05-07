package com.ke.utopia.agent.memory.memory;

import com.ke.utopia.agent.memory.model.*;
import com.ke.utopia.agent.memory.spi.EmbeddingService;
import com.ke.utopia.agent.memory.spi.MemoryStorage;
import com.ke.utopia.agent.memory.spi.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 记忆衰退引擎：定期评估记忆活跃度，低于阈值的降级到 ARCHIVED 层。
 */
public final class MemoryDecayEngine {

    private static final Logger log = LoggerFactory.getLogger(MemoryDecayEngine.class);

    private final MemoryStorage storage;
    private final MemoryDecayStrategy strategy;
    private final double threshold;
    private final double importanceProtectionThreshold;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private ScheduledExecutorService scheduler;

    public MemoryDecayEngine(MemoryStorage storage, MemoryDecayStrategy strategy,
                              double threshold, double importanceProtectionThreshold,
                              EmbeddingService embeddingService, VectorStore vectorStore) {
        this.storage = storage;
        this.strategy = strategy;
        this.threshold = threshold;
        this.importanceProtectionThreshold = importanceProtectionThreshold;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
    }

    /**
     * 执行一次衰退评估。
     * 遍历用户的所有 CORE 记忆，低于阈值的降级到 ARCHIVED。
     */
    public void runDecayCycle(String userId) {
        Instant now = Instant.now();
        List<MemoryEntry> coreEntries = storage.getMemoryEntriesByTier(userId, MemoryTier.CORE);

        int archived = 0;
        int protected_ = 0;

        for (MemoryEntry entry : coreEntries) {
            // Protect important memories
            if (entry.getImportanceScore() >= importanceProtectionThreshold) {
                protected_++;
                continue;
            }

            double score = strategy.calculateScore(entry, now);
            if (score < threshold) {
                boolean updated = storage.updateMemoryTier(userId, entry.getId(), MemoryTier.ARCHIVED);
                if (updated) {
                    archived++;
                    log.debug("Decayed memory {} for user {} (score={:.4f}, threshold={:.4f})",
                            entry.getId(), userId, score, threshold);
                }
            }
        }

        if (archived > 0 || protected_ > 0) {
            log.info("Decay cycle completed for user {}: archived={}, protected={}, total={}",
                    userId, archived, protected_, coreEntries.size());
        }
    }

    /**
     * 启动定时衰退任务。
     */
    public void startScheduledDecay(long intervalMinutes) {
        if (scheduler != null && !scheduler.isShutdown()) {
            log.warn("Decay scheduler already running");
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "memory-decay-engine");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(() -> {
            try {
                List<String> allUsers = storage.getAllUserIds();
                if (allUsers.isEmpty()) {
                    log.debug("Scheduled decay cycle triggered: no users");
                } else {
                    log.debug("Scheduled decay cycle triggered: {} users", allUsers.size());
                    for (String userId : allUsers) {
                        try {
                            runDecayCycle(userId);
                        } catch (Exception e) {
                            log.error("Error in decay cycle for user {}: {}", userId, e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error in scheduled decay cycle", e);
            }
        }, intervalMinutes, intervalMinutes, TimeUnit.MINUTES);

        log.info("Memory decay engine started with interval={}min, threshold={}, strategy={}",
                intervalMinutes, threshold, strategy.getName());
    }

    /**
     * 停止定时任务。
     */
    public void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("Memory decay engine stopped");
        }
    }
}
