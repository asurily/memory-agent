package com.ke.utopia.agent.memory.summary;

import com.ke.utopia.agent.memory.model.*;
import com.ke.utopia.agent.memory.security.SecurityScanner;
import com.ke.utopia.agent.memory.spi.IntentSummarizer;
import com.ke.utopia.agent.memory.spi.MemoryStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 负责编排记忆提取流程。
 * 流程：调用 IntentSummarizer.extractMemories() → 安全扫描 → 去重 → 置信度过滤 → 写入存储
 */
public final class MemoryExtractor {

    private static final Logger log = LoggerFactory.getLogger(MemoryExtractor.class);

    private final IntentSummarizer summarizer;
    private final SecurityScanner securityScanner;
    private final MemoryStorage storage;
    private final double confidenceThreshold;

    public MemoryExtractor(IntentSummarizer summarizer, SecurityScanner securityScanner,
                           MemoryStorage storage, double confidenceThreshold) {
        this.summarizer = summarizer;
        this.securityScanner = securityScanner;
        this.storage = storage;
        this.confidenceThreshold = confidenceThreshold;
    }

    /**
     * 从对话消息中提取记忆并写入存储。
     *
     * @param userId         用户ID
     * @param sessionId      会话ID
     * @param messages       对话消息列表
     * @param existingProfile 已有的用户画像
     * @return 成功写入的记忆条目列表
     */
    public List<MemoryEntry> extractAndStore(String userId, String sessionId,
                                              List<ConversationMessage> messages,
                                              Optional<UserProfile> existingProfile) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. 调用 LLM 提取记忆
        List<MemoryExtraction> extractions;
        try {
            extractions = summarizer.extractMemories(messages, existingProfile);
        } catch (Exception e) {
            log.warn("Failed to extract memories for session {}: {}", sessionId, e.getMessage());
            return Collections.emptyList();
        }

        if (extractions.isEmpty()) {
            log.debug("No memories extracted from session {}", sessionId);
            return Collections.emptyList();
        }

        // 2. 过滤低置信度
        List<MemoryExtraction> filtered = extractions.stream()
                .filter(e -> e.getConfidence() >= confidenceThreshold)
                .collect(Collectors.toList());

        // 3. 安全扫描 + 去重 + 写入
        List<MemoryEntry> saved = new ArrayList<>();
        for (MemoryExtraction extraction : filtered) {
            try {
                securityScanner.scanAndThrow(extraction.getContent());
            } catch (Exception e) {
                log.warn("Security scan blocked memory extraction: {}", e.getMessage());
                continue;
            }

            // 去重检查
            List<MemoryEntry> existing = storage.getMemoryEntries(userId, extraction.getType());
            boolean duplicate = existing.stream()
                    .anyMatch(e -> e.getContent().equals(extraction.getContent()));
            if (duplicate) {
                log.debug("Duplicate memory skipped: {}", extraction.getContent());
                continue;
            }

            MemoryEntry entry = MemoryEntry.reconstruct(
                    java.util.UUID.randomUUID().toString(),
                    extraction.getContent(),
                    extraction.getType(),
                    java.time.Instant.now(),
                    java.time.Instant.now(),
                    java.time.Instant.now(),
                    0,
                    extraction.getImportanceScore(),
                    MemoryTier.CORE);
            storage.addMemoryEntry(userId, entry);
            saved.add(entry);
            log.debug("Auto-extracted memory for user {}: [{}] {}",
                    userId, extraction.getCategory(), extraction.getContent());
        }

        if (!saved.isEmpty()) {
            log.info("Auto-extracted {} memories for user {} from session {}",
                    saved.size(), userId, sessionId);
        }
        return saved;
    }
}
