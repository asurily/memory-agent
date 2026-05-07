package com.ke.utopia.agent.memory.summary;

import com.ke.utopia.agent.memory.exception.SessionNotFoundException;
import com.ke.utopia.agent.memory.exception.SummarizationException;
import com.ke.utopia.agent.memory.model.*;
import com.ke.utopia.agent.memory.spi.IntentSummarizer;
import com.ke.utopia.agent.memory.spi.MemoryStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 意图总结引擎：编排何时总结、如何总结。
 */
public final class IntentSummarizationEngine {

    private static final Logger log = LoggerFactory.getLogger(IntentSummarizationEngine.class);

    private final IntentSummarizer summarizer;
    private final MemoryStorage storage;
    private final SummarizationConfig config;
    private final MemoryExtractor memoryExtractor;

    public IntentSummarizationEngine(IntentSummarizer summarizer,
                                      MemoryStorage storage,
                                      SummarizationConfig config) {
        this(summarizer, storage, config, null);
    }

    public IntentSummarizationEngine(IntentSummarizer summarizer,
                                      MemoryStorage storage,
                                      SummarizationConfig config,
                                      MemoryExtractor memoryExtractor) {
        this.summarizer = summarizer;
        this.storage = storage;
        this.config = config;
        this.memoryExtractor = memoryExtractor;
    }

    /**
     * 判断是否应该触发总结。
     */
    public boolean shouldSummarize(String sessionId) {
        int messageCount = storage.getMessageCount(sessionId);
        if (messageCount >= config.getMessageCountThreshold()) {
            return true;
        }
        // 粗略 token 估算：中文约 1.5 字符/token，英文约 4 字符/token，取中间值 2
        List<ConversationMessage> messages = storage.getMessages(sessionId);
        int estimatedTokens = messages.stream()
                .mapToInt(m -> m.getContent() != null ? m.getContent().length() / 2 : 0)
                .sum();
        return estimatedTokens >= config.getTokenCountThreshold();
    }

    /**
     * 执行意图总结。
     */
    public IntentSummary summarize(String sessionId) {
        Session session = storage.getSession(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));
        String userId = session.getUserId();

        List<ConversationMessage> messages = storage.getMessages(sessionId);
        if (messages.isEmpty()) {
            throw new SummarizationException("Cannot summarize empty conversation");
        }

        Optional<UserProfile> userProfile = Optional.of(storage.getUserProfile(userId));
        Optional<List<IntentSummary>> previousSummaries =
                Optional.of(storage.getIntentSummaries(sessionId));

        log.info("Summarizing session {} ({} messages) via {}",
                sessionId, messages.size(), summarizer.getModelIdentifier());

        try {
            IntentSummary summary = summarizer.summarize(
                    userId, sessionId, messages, userProfile, previousSummaries);
            storage.saveIntentSummary(summary);
            log.info("Summary saved for session {}: {}", sessionId, summary.getCoreIntent());

            // Auto memory extraction
            if (memoryExtractor != null) {
                try {
                    memoryExtractor.extractAndStore(userId, sessionId, messages, userProfile);
                } catch (Exception e) {
                    log.warn("Auto memory extraction failed for session {}: {}", sessionId, e.getMessage());
                }
            }

            return summary;
        } catch (Exception e) {
            throw new SummarizationException("Failed to summarize session " + sessionId, e);
        }
    }

    /**
     * 异步版本。
     */
    public CompletableFuture<IntentSummary> summarizeAsync(String sessionId) {
        Session session = storage.getSession(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));
        String userId = session.getUserId();

        List<ConversationMessage> messages = storage.getMessages(sessionId);
        Optional<UserProfile> userProfile = Optional.of(storage.getUserProfile(userId));
        Optional<List<IntentSummary>> previousSummaries =
                Optional.of(storage.getIntentSummaries(sessionId));

        return summarizer.summarizeAsync(userId, sessionId, messages, userProfile, previousSummaries)
                .thenApply(summary -> {
                    storage.saveIntentSummary(summary);
                    log.info("Async summary saved for session {}: {}", sessionId, summary.getCoreIntent());
                    return summary;
                });
    }

    /**
     * 会话关闭时总结。
     */
    public IntentSummary summarizeOnClose(String sessionId) {
        if (!config.isSummarizeOnClose()) {
            return null;
        }
        int messageCount = storage.getMessageCount(sessionId);
        if (messageCount == 0) {
            log.debug("Skipping summary for empty session {}", sessionId);
            return null;
        }
        return summarize(sessionId);
    }
}
