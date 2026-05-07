package com.ke.utopia.agent.memory;

import com.ke.utopia.agent.memory.compression.CompressionConfig;
import com.ke.utopia.agent.memory.compression.CompressionResult;
import com.ke.utopia.agent.memory.compression.ContextCompressor;
import com.ke.utopia.agent.memory.config.MemoryAgentConfig;
import com.ke.utopia.agent.memory.conversation.ConversationManager;
import com.ke.utopia.agent.memory.exception.SummarizationException;
import com.ke.utopia.agent.memory.intent.IncrementalIntentEngine;
import com.ke.utopia.agent.memory.intent.IntentContext;
import com.ke.utopia.agent.memory.memory.*;
import com.ke.utopia.agent.memory.model.*;
import com.ke.utopia.agent.memory.security.SecurityScanner;
import com.ke.utopia.agent.memory.spi.EmbeddingService;
import com.ke.utopia.agent.memory.spi.IntentSummarizer;
import com.ke.utopia.agent.memory.spi.KeywordSearchService;
import com.ke.utopia.agent.memory.spi.MemoryStorage;
import com.ke.utopia.agent.memory.spi.MemoryStorageProvider;
import com.ke.utopia.agent.memory.spi.PromptStrategy;
import com.ke.utopia.agent.memory.spi.VectorStore;
import com.ke.utopia.agent.memory.spi.defaults.InMemoryKeywordSearchService;
import com.ke.utopia.agent.memory.spi.defaults.InMemoryMemoryStorage;
import com.ke.utopia.agent.memory.spi.defaults.InMemoryVectorStore;
import com.ke.utopia.agent.memory.spi.defaults.OpenAIIntentSummarizer;
import com.ke.utopia.agent.memory.spi.defaults.SimpleEmbeddingService;
import com.ke.utopia.agent.memory.summary.IntentSummarizationEngine;
import com.ke.utopia.agent.memory.summary.MemoryExtractor;
import com.ke.utopia.agent.memory.summary.SummarizationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Memory Agent 主门面类。
 * 聚合所有子系统：会话管理、策划记忆、意图总结、上下文压缩、安全扫描。
 *
 * <p>纯 Java 使用示例：
 * <pre>
 *   MemoryAgent agent = MemoryAgent.builder()
 *       .config(MemoryAgentConfig.builder()
 *           .llmApiKey("sk-xxx")
 *           .build())
 *       .build();
 *
 *   Session session = agent.createSession("user-123", "api");
 *   agent.addUserMessage(session.getId(), "我想构建一个订单管理API");
 *   agent.addAssistantMessage(session.getId(), "好的，我来帮你...");
 *   IntentSummary summary = agent.summarize(session.getId());
 * </pre>
 */
public final class MemoryAgent {

    private static final Logger log = LoggerFactory.getLogger(MemoryAgent.class);

    private final ConversationManager conversationManager;
    private final CuratedMemoryManager memoryManager;
    private final IntentSummarizationEngine summarizationEngine;
    private final ContextCompressor contextCompressor;
    private final SecurityScanner securityScanner;
    private final MemoryStorage storage;
    private final MemoryAgentConfig config;

    // Feature 1: Auto Memory Extraction
    private final MemoryExtractor memoryExtractor;

    // Feature 2: Semantic Search
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;

    // Feature 3: Memory Decay
    private final MemoryDecayEngine decayEngine;

    // Feature 4: Conflict Detection
    private final MemoryConflictDetector conflictDetector;
    private final ExecutorService conflictExecutor;

    // Feature 5: Tiered Memory
    private final MemoryTierManager tierManager;

    // Feature 6: Hybrid Search (D3)
    private final KeywordSearchService keywordSearchService;

    // Feature 7: Auto Importance Learning (D6)
    private final RelevanceTracker relevanceTracker;

    // Feature 8: Metrics (D7)
    private final MemoryMetricsCollector metricsCollector;

    // Feature 9: Incremental Intent
    private final IncrementalIntentEngine incrementalIntentEngine;

    // Session-level frozen snapshots: sessionId -> MemorySnapshot
    private final Map<String, MemorySnapshot> sessionSnapshots = new ConcurrentHashMap<>();

    // Feature 9: Auto Pipeline
    private final ExecutorService pipelineExecutor;
    private final Set<String> summarizationInProgress = ConcurrentHashMap.newKeySet();

    private MemoryAgent(Builder builder) {
        this.config = builder.config;

        // Resolve storage
        this.storage = builder.storage != null ? builder.storage : resolveStorage(builder.config);
        this.storage.initialize();

        // Security scanner (always enabled)
        this.securityScanner = new SecurityScanner();

        // Conversation manager
        this.conversationManager = new ConversationManager(storage);

        // Intent summarizer
        IntentSummarizer summarizer = builder.summarizer != null
                ? builder.summarizer
                : resolveSummarizer(builder.config, builder.promptStrategy);

        // Detect LLM availability (for auto-enabling LLM-dependent features)
        boolean hasLlm = builder.summarizer != null
                || (builder.config.getLlmApiKey() != null && !builder.config.getLlmApiKey().isEmpty());

        // Semantic Search (always enabled with defaults)
        this.embeddingService = builder.embeddingService != null
                ? builder.embeddingService
                : new SimpleEmbeddingService(builder.config.getEmbeddingDimension());
        this.vectorStore = builder.vectorStore != null
                ? builder.vectorStore
                : new InMemoryVectorStore();
        this.vectorStore.initialize();

        // Conflict Detection (auto-detect from LLM availability)
        this.conflictDetector = hasLlm
                ? new MemoryConflictDetector(summarizer)
                : null;

        // Async conflict executor (when LLM available and mode is ASYNC)
        if (hasLlm && builder.config.getConflictDetectionMode() == MemoryAgentConfig.ConflictDetectionMode.ASYNC) {
            this.conflictExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "conflict-detection");
                t.setDaemon(true);
                return t;
            });
        } else {
            this.conflictExecutor = null;
        }

        // Relevance Tracker (always enabled)
        this.relevanceTracker = new RelevanceTracker();

        // Metrics Collector (always enabled)
        this.metricsCollector = new MemoryMetricsCollector();

        // Hybrid Search (always enabled with defaults)
        this.keywordSearchService = builder.keywordSearchService != null
                ? builder.keywordSearchService
                : new InMemoryKeywordSearchService();
        this.keywordSearchService.initialize();

        // Memory manager
        this.memoryManager = new CuratedMemoryManager(
                storage, securityScanner,
                config.getMemoryCharLimit(), config.getUserProfileCharLimit(),
                config, embeddingService, vectorStore, conflictDetector,
                conflictExecutor, relevanceTracker, metricsCollector, keywordSearchService);

        // Auto Memory Extraction (auto-detect from LLM availability)
        if (hasLlm) {
            this.memoryExtractor = new MemoryExtractor(
                    summarizer, securityScanner, storage,
                    builder.config.getMemoryExtractionConfidenceThreshold());
        } else {
            this.memoryExtractor = null;
        }

        // Summarization engine (with optional auto extraction)
        SummarizationConfig summaryConfig = SummarizationConfig.builder()
                .messageCountThreshold(config.getSummarizationMessageThreshold())
                .tokenCountThreshold(config.getSummarizationTokenThreshold())
                .summarizeOnClose(config.isSummarizeOnSessionClose())
                .build();
        this.summarizationEngine = new IntentSummarizationEngine(
                summarizer, storage, summaryConfig, memoryExtractor);

        // Context compressor
        CompressionConfig compressionConfig = CompressionConfig.builder()
                .compressionThresholdRatio(config.getCompressionThresholdRatio())
                .protectFirstN(config.getCompressionProtectFirstN())
                .build();
        this.contextCompressor = new ContextCompressor(summarizer, compressionConfig);

        // Tiered Memory
        this.tierManager = new MemoryTierManager(storage, embeddingService, vectorStore);

        // Memory Decay (always enabled)
        MemoryDecayStrategy strategy = new CompositeDecayStrategy(builder.config.getDecayHalfLifeDays());
        this.decayEngine = new MemoryDecayEngine(
                storage, strategy,
                builder.config.getDecayThreshold(),
                builder.config.getImportanceProtectionThreshold(),
                embeddingService, vectorStore);
        this.decayEngine.startScheduledDecay(builder.config.getDecayScheduleIntervalMinutes());

        // Incremental Intent Engine (always enabled)
        this.incrementalIntentEngine = new IncrementalIntentEngine(builder.config, summarizer);

        // Auto Pipeline (always enabled, ASYNC mode)
        this.pipelineExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "auto-pipeline");
            t.setDaemon(true);
            return t;
        });

        log.info("MemoryAgent initialized: storage={}, summarizer={}, hasLlm={}, tiered=true",
                storage.getClass().getSimpleName(),
                summarizer.getClass().getSimpleName(),
                hasLlm);
    }

    public static Builder builder() {
        return new Builder();
    }

    // --- Session ---

    public Session createSession(String userId, String source) {
        Session session = conversationManager.createSession(userId, source);
        // Capture frozen snapshot for this session
        MemorySnapshot snapshot = memoryManager.captureSnapshot(userId);
        sessionSnapshots.put(session.getId(), snapshot);
        return session;
    }

    public Optional<Session> getSession(String sessionId) {
        return conversationManager.getSession(sessionId);
    }

    public List<Session> listSessions(String userId) {
        return conversationManager.listSessions(userId);
    }

    public Session closeSession(String sessionId) {
        // Summarize before close
        try {
            summarizationEngine.summarizeOnClose(sessionId);
        } catch (Exception e) {
            log.warn("Failed to summarize session {} on close: {}", sessionId, e.getMessage());
        }
        // Clear incremental intent cache
        clearIncrementalIntentCache(sessionId);
        Session closed = conversationManager.closeSession(sessionId);
        sessionSnapshots.remove(sessionId);
        return closed;
    }

    // --- Conversation ---

    public ConversationMessage addUserMessage(String sessionId, String content) {
        return conversationManager.addUserMessage(sessionId, content);
    }

    public ConversationMessage addAssistantMessage(String sessionId, String content) {
        return conversationManager.addAssistantMessage(sessionId, content);
    }

    /**
     * 一站式处理用户消息：记录消息、加载记忆上下文、检查总结条件、推导增量意图。
     * 将调用方原本需要 6+ 步的手动调用合并为一步。
     *
     * @param sessionId 会话 ID
     * @param content   用户输入内容
     * @return TurnContext 聚合了本轮消息、记忆快照、用户画像、最近总结、是否建议总结、增量意图
     */
    public TurnContext processUserMessage(String sessionId, String content) {
        // 1. 记录用户消息（同时验证 session 存在）
        ConversationMessage userMessage = addUserMessage(sessionId, content);

        // 2. 获取 userId
        String userId = getSession(sessionId)
                .orElseThrow(() -> new com.ke.utopia.agent.memory.exception.SessionNotFoundException(sessionId))
                .getUserId();

        // 3. 获取冻结快照
        MemorySnapshot memorySnapshot = getSessionSnapshot(sessionId);

        // 4. 获取用户画像
        UserProfile userProfile = getUserProfile(userId);

        // 5. 获取最近总结
        List<IntentSummary> recentSummaries = getIntentSummaries(sessionId);

        // 6. 获取消息列表
        List<ConversationMessage> messages = getMessages(sessionId);

        // 7. 检查总结条件
        boolean summarizationRecommended = shouldSummarize(sessionId);

        // 8. 推导增量意图
        IncrementalIntent incrementalIntent = null;
        try {
            IntentContext context = IntentContext.builder()
                    .sessionMessages(messages)
                    .memorySnapshot(memorySnapshot)
                    .userProfile(userProfile)
                    .recentSummaries(recentSummaries)
                    .build();
            incrementalIntent = incrementalIntentEngine.deriveIntent(sessionId, userMessage, context);
        } catch (Exception e) {
            log.warn("Failed to derive incremental intent for session {}: {}", sessionId, e.getMessage());
        }

        // 9. 组装 TurnContext + 自动总结
        CompletableFuture<IntentSummary> pendingSummarization = maybeAutoSummarize(sessionId, summarizationRecommended);

        return TurnContext.builder()
                .userMessage(userMessage)
                .sessionMessages(messages)
                .memorySnapshot(memorySnapshot)
                .userProfile(userProfile)
                .recentSummaries(recentSummaries)
                .summarizationRecommended(summarizationRecommended)
                .incrementalIntent(incrementalIntent)
                .pendingSummarization(pendingSummarization)
                .build();
    }

    /**
     * 记录助手消息，语义化封装 {@link #addAssistantMessage(String, String)}。
     * 与 {@link #processUserMessage(String, String)} 配对使用。
     *
     * @param sessionId 会话 ID
     * @param content   助手回复内容
     * @return 已保存的助手消息
     */
    public ConversationMessage recordAssistantMessage(String sessionId, String content) {
        ConversationMessage message = addAssistantMessage(sessionId, content);
        maybeAutoCompress(sessionId);
        return message;
    }

    public List<ConversationMessage> getMessages(String sessionId) {
        return conversationManager.getMessages(sessionId);
    }

    public List<ConversationMessage> getRecentMessages(String sessionId, int limit) {
        return conversationManager.getRecentMessages(sessionId, limit);
    }

    // --- Memory ---

    public MemorySnapshot captureMemorySnapshot(String userId) {
        return memoryManager.captureSnapshot(userId);
    }

    public MemorySnapshot getSessionSnapshot(String sessionId) {
        return sessionSnapshots.getOrDefault(sessionId, MemorySnapshot.empty());
    }

    public MemoryEntry addMemory(String userId, String content, MemoryType type) {
        return memoryManager.addEntry(userId, content, type);
    }

    public List<MemoryEntry> getMemories(String userId, MemoryType type) {
        return memoryManager.getEntries(userId, type);
    }

    public boolean removeMemory(String userId, String entryId) {
        return memoryManager.removeEntry(userId, entryId);
    }

    public UserProfile getUserProfile(String userId) {
        return memoryManager.getUserProfile(userId);
    }

    // --- Intent Summarization ---

    public boolean shouldSummarize(String sessionId) {
        return summarizationEngine.shouldSummarize(sessionId);
    }

    public IntentSummary summarize(String sessionId) {
        long startNs = System.nanoTime();
        try {
            return summarizationEngine.summarize(sessionId);
        } finally {
            if (metricsCollector != null) {
                metricsCollector.recordSummarize(System.nanoTime() - startNs);
            }
        }
    }

    public CompletableFuture<IntentSummary> summarizeAsync(String sessionId) {
        return summarizationEngine.summarizeAsync(sessionId);
    }

    /**
     * 获取指定会话的意图总结列表。
     */
    public List<IntentSummary> getIntentSummaries(String sessionId) {
        return storage.getIntentSummaries(sessionId);
    }

    /**
     * 获取指定用户的所有意图总结。
     */
    public List<IntentSummary> getIntentSummariesByUser(String userId) {
        return storage.getIntentSummariesByUser(userId);
    }

    // --- Context Compression ---

    public boolean shouldCompress(String sessionId, int contextWindowTokens) {
        List<ConversationMessage> messages = conversationManager.getMessages(sessionId);
        return contextCompressor.shouldCompress(messages, contextWindowTokens);
    }

    public CompressionResult compress(String sessionId, int contextWindowTokens) {
        List<ConversationMessage> messages = conversationManager.getMessages(sessionId);
        return contextCompressor.compress(messages, contextWindowTokens);
    }

    // --- Security ---

    public SecurityScanResult scanContent(String content) {
        return securityScanner.scan(content);
    }

    // --- Feature 1: Auto Memory Extraction ---

    /**
     * 手动触发记忆提取。
     */
    public List<MemoryEntry> extractMemories(String sessionId) {
        Session session = storage.getSession(sessionId)
                .orElseThrow(() -> new com.ke.utopia.agent.memory.exception.SessionNotFoundException(sessionId));
        String userId = session.getUserId();
        List<ConversationMessage> messages = storage.getMessages(sessionId);
        Optional<UserProfile> userProfile = Optional.of(storage.getUserProfile(userId));

        if (memoryExtractor != null) {
            List<MemoryEntry> extracted = memoryExtractor.extractAndStore(userId, sessionId, messages, userProfile);
            if (metricsCollector != null) {
                metricsCollector.recordMemoryExtraction();
            }
            return extracted;
        }
        return Collections.emptyList();
    }

    // --- Feature 2: Semantic Search + D3 Hybrid Search + D6 Relevance Tracking ---

    /**
     * 搜索记忆（支持混合检索和相关性跟踪）。
     */
    public List<VectorSearchResult> searchMemories(String userId, String query, int topK) {
        long startNs = System.nanoTime();
        try {
            if (embeddingService == null || vectorStore == null) {
                log.warn("Semantic search not enabled");
                return Collections.emptyList();
            }

            // D3: Hybrid search (vector + keyword RRF fusion)
            if (keywordSearchService != null) {
                // Get vector results
                float[] queryEmbedding = embeddingService.embed(query);
                Map<String, String> filter = new HashMap<>();
                filter.put("userId", userId);
                List<VectorSearchResult> vectorResults = vectorStore.search(queryEmbedding, topK * 2, filter);

                // Get keyword results
                List<VectorSearchResult> keywordResults = keywordSearchService.search(query, topK * 2, userId);

                // RRF fusion
                List<VectorSearchResult> fused = HybridSearchStrategy.rrf(vectorResults, keywordResults, topK, 60);

                // D6: Relevance tracking
                if (relevanceTracker != null) {
                    for (VectorSearchResult r : fused) {
                        trackRelevance(userId, r);
                    }
                }

                if (metricsCollector != null) {
                    metricsCollector.recordSearch(System.nanoTime() - startNs, fused.size());
                }
                return fused;
            }

            // Pure semantic search
            float[] queryEmbedding = embeddingService.embed(query);
            Map<String, String> filter = new HashMap<>();
            filter.put("userId", userId);

            List<VectorSearchResult> results = vectorStore.search(queryEmbedding, topK, filter);

            // D6: Relevance tracking
            if (relevanceTracker != null) {
                for (VectorSearchResult r : results) {
                    trackRelevance(userId, r);
                }
            }

            if (metricsCollector != null) {
                metricsCollector.recordSearch(System.nanoTime() - startNs, results.size());
            }
            return results;
        } catch (Exception e) {
            if (metricsCollector != null) {
                metricsCollector.recordError();
            }
            log.error("Search failed for user {}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 语义搜索对话消息。
     */
    public List<VectorSearchResult> searchMessages(String query, String sessionId, int topK) {
        if (embeddingService == null || vectorStore == null) {
            log.warn("Semantic search not enabled");
            return Collections.emptyList();
        }

        // Fallback to keyword search for messages (messages are not vectorized by default)
        return Collections.emptyList();
    }

    // --- Feature 3: Memory Decay ---

    /**
     * 手动触发衰退评估。
     */
    public void runDecayCycle(String userId) {
        long startNs = System.nanoTime();
        try {
            if (decayEngine != null) {
                decayEngine.runDecayCycle(userId);
            }
        } finally {
            if (metricsCollector != null) {
                metricsCollector.recordDecayRun(System.nanoTime() - startNs);
            }
        }
    }

    /**
     * 更新记忆重要性评分。
     */
    public void updateMemoryImportance(String userId, String entryId, double score) {
        memoryManager.updateImportanceScore(userId, entryId, score);
    }

    // --- Feature 5: Tiered Memory ---

    /**
     * 获取归档记忆。
     */
    public List<MemoryEntry> getArchivedMemories(String userId) {
        if (tierManager != null) {
            return tierManager.getArchivedMemories(userId, Integer.MAX_VALUE);
        }
        return storage.getMemoryEntriesByTier(userId, MemoryTier.ARCHIVED);
    }

    /**
     * 语义搜索归档记忆。
     */
    public List<VectorSearchResult> searchArchivedMemories(String userId, String query, int topK) {
        if (tierManager != null) {
            return tierManager.searchArchivedMemories(userId, query, topK);
        }
        return Collections.emptyList();
    }

    /**
     * 归档记忆（降级到 ARCHIVED 层）。
     */
    public void archiveMemory(String userId, String entryId) {
        if (tierManager != null) {
            tierManager.archiveEntry(userId, entryId);
        }
    }

    /**
     * 提升记忆到 CORE 层。
     */
    public void promoteMemory(String userId, String entryId) {
        if (tierManager != null) {
            tierManager.promoteToCore(userId, entryId);
        }
    }

    // --- Feature 8: Metrics (D7) ---

    /**
     * 获取性能指标快照。
     */
    public MemoryMetricsCollector.MetricsSnapshot getMetrics() {
        if (metricsCollector != null) {
            return metricsCollector.snapshot();
        }
        return null;
    }

    // --- Lifecycle ---

    public void shutdown() {
        if (decayEngine != null) {
            decayEngine.stop();
        }
        if (pipelineExecutor != null) {
            pipelineExecutor.shutdown();
            try {
                if (!pipelineExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    pipelineExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                pipelineExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        if (conflictExecutor != null) {
            conflictExecutor.shutdown();
        }
        if (vectorStore != null) {
            vectorStore.shutdown();
        }
        if (keywordSearchService != null) {
            keywordSearchService.shutdown();
        }
        storage.shutdown();
        sessionSnapshots.clear();

        // Clear incremental intent engine caches
        if (incrementalIntentEngine != null) {
            // Engine will be garbage collected, but we could add explicit cleanup if needed
        }

        if (metricsCollector != null) {
            MemoryMetricsCollector.MetricsSnapshot snap = metricsCollector.snapshot();
            log.info("MemoryAgent metrics: addMemory={} calls, search={} calls, " +
                            "summarize={} calls, decayRuns={}, errors={}, " +
                            "avgAddLatency={}ms, avgSearchLatency={}ms",
                    snap.getAddMemoryCount(), snap.getSearchCount(),
                    snap.getSummarizeCount(), snap.getDecayRunCount(),
                    snap.getErrorCount(),
                    String.format("%.2f", snap.getAddMemoryAvgLatencyMs()),
                    String.format("%.2f", snap.getSearchAvgLatencyMs()));
        }

        log.info("MemoryAgent shut down");
    }

    /**
     * 获取上一轮增量意图（用于调试或自定义处理）。
     */
    public java.util.Optional<IncrementalIntent> getPreviousIncrementalIntent(String sessionId) {
        if (incrementalIntentEngine != null) {
            return incrementalIntentEngine.getPreviousIntent(sessionId);
        }
        return java.util.Optional.empty();
    }

    /**
     * 清除会话的增量意图缓存（会话结束时自动调用）。
     */
    public void clearIncrementalIntentCache(String sessionId) {
        if (incrementalIntentEngine != null) {
            incrementalIntentEngine.clearSession(sessionId);
        }
    }

    // --- Auto Pipeline ---

    /**
     * 自动总结：若条件满足，触发异步或同步总结。
     *
     * @return CompletableFuture（异步时由调用方可选等待），或 null（未触发）
     */
    private CompletableFuture<IntentSummary> maybeAutoSummarize(String sessionId, boolean recommended) {
        if (!recommended) {
            log.debug("Auto-summarization skipped for session {}: not recommended", sessionId);
            return null;
        }
        if (!summarizationInProgress.add(sessionId)) {
            log.debug("Auto-summarization skipped for session {}: already in progress", sessionId);
            return null;
        }

        CompletableFuture<IntentSummary> future = new CompletableFuture<>();
        future.whenComplete((result, ex) -> summarizationInProgress.remove(sessionId));

        Runnable task = () -> {
            try {
                log.info("Auto-summarization started for session {}", sessionId);
                IntentSummary summary = summarize(sessionId);
                if (metricsCollector != null) {
                    metricsCollector.recordAutoSummarize();
                }
                future.complete(summary);
                log.info("Auto-summarization completed for session {}", sessionId);
            } catch (Exception e) {
                log.warn("Auto-summarization failed for session {}: {}", sessionId, e.getMessage());
                if (metricsCollector != null) {
                    metricsCollector.recordError();
                }
                future.completeExceptionally(e);
            }
        };

        pipelineExecutor.submit(task);

        return future;
    }

    /**
     * 自动压缩：若条件满足，触发异步压缩。Fire-and-forget。
     */
    private void maybeAutoCompress(String sessionId) {
        try {
            if (!shouldCompress(sessionId, config.getAutoCompressionContextWindowTokens())) {
                log.debug("Auto-compression skipped for session {}: not needed", sessionId);
                return;
            }

            Runnable task = () -> {
                try {
                    log.info("Auto-compression started for session {}", sessionId);
                    compress(sessionId, config.getAutoCompressionContextWindowTokens());
                    if (metricsCollector != null) {
                        metricsCollector.recordAutoCompress();
                    }
                    log.info("Auto-compression completed for session {}", sessionId);
                } catch (Exception e) {
                    log.warn("Auto-compression failed for session {}: {}", sessionId, e.getMessage());
                    if (metricsCollector != null) {
                        metricsCollector.recordError();
                    }
                }
            };

            pipelineExecutor.submit(task);
        } catch (Exception e) {
            log.warn("Auto-compression check failed for session {}: {}", sessionId, e.getMessage());
        }
    }

    // --- Internal ---

    private void trackRelevance(String userId, VectorSearchResult result) {
        if (relevanceTracker == null) return;
        if (relevanceTracker.recordRetrieval(result.getId())) {
            Optional<MemoryEntry> entryOpt = storage.getMemoryEntry(userId, result.getId());
            entryOpt.ifPresent(entry -> {
                double boostedScore = relevanceTracker.calculateBoostedScore(entry.getImportanceScore());
                storage.updateMemoryEntry(userId, entry.withImportanceScore(boostedScore));
                log.debug("Boosted importance for entry {}: {} -> {}", entry.getId(), entry.getImportanceScore(), boostedScore);
            });
        }
    }

    private static MemoryStorage resolveStorage(MemoryAgentConfig config) {
        // Try ServiceLoader discovery
        ServiceLoader<MemoryStorageProvider> loader = ServiceLoader.load(MemoryStorageProvider.class);
        List<MemoryStorageProvider> providers = new ArrayList<>();
        loader.forEach(providers::add);

        if (!providers.isEmpty()) {
            // Sort by priority, pick the one matching config or highest priority
            providers.sort(Comparator.comparingInt(MemoryStorageProvider::priority));
            for (MemoryStorageProvider provider : providers) {
                if (provider.name().equals(config.getStorageType())) {
                    log.info("Resolved storage via SPI: {}", provider.name());
                    return provider.create(config);
                }
            }
            // Fallback to highest priority
            return providers.get(0).create(config);
        }

        // Default fallback
        log.info("No SPI provider found, using default InMemoryMemoryStorage");
        return new InMemoryMemoryStorage();
    }

    private static IntentSummarizer resolveSummarizer(MemoryAgentConfig config, PromptStrategy promptStrategy) {
        if (config.getLlmApiKey() != null && !config.getLlmApiKey().isEmpty()) {
            return new OpenAIIntentSummarizer(config, promptStrategy);
        }
        // No LLM configured, return a stub that throws on use
        return new NoOpIntentSummarizer();
    }

    /**
     * Stub summarizer used when no LLM is configured.
     */
    private static class NoOpIntentSummarizer implements IntentSummarizer {
        @Override
        public IntentSummary summarize(String userId, String sessionId,
                                        List<ConversationMessage> messages,
                                        Optional<UserProfile> userProfile,
                                        Optional<List<IntentSummary>> previousSummaries) {
            throw new SummarizationException("No LLM configured. Please provide an IntentSummarizer or set llmApiKey.");
        }

        @Override
        public CompletableFuture<IntentSummary> summarizeAsync(String userId, String sessionId,
                                                                List<ConversationMessage> messages,
                                                                Optional<UserProfile> userProfile,
                                                                Optional<List<IntentSummary>> previousSummaries) {
            return CompletableFuture.failedFuture(
                    new SummarizationException("No LLM configured."));
        }

        @Override
        public String compressConversation(List<ConversationMessage> messages, int targetTokenBudget) {
            throw new SummarizationException("No LLM configured.");
        }

        @Override
        public String getModelIdentifier() {
            return "none";
        }
    }

    // --- Builder ---

    public static class Builder {
        private MemoryAgentConfig config = MemoryAgentConfig.builder().build();
        private MemoryStorage storage;
        private IntentSummarizer summarizer;
        private EmbeddingService embeddingService;
        private VectorStore vectorStore;
        private KeywordSearchService keywordSearchService;
        private PromptStrategy promptStrategy;

        public Builder config(MemoryAgentConfig config) {
            this.config = config;
            return this;
        }

        public Builder storage(MemoryStorage storage) {
            this.storage = storage;
            return this;
        }

        public Builder summarizer(IntentSummarizer summarizer) {
            this.summarizer = summarizer;
            return this;
        }

        public Builder embeddingService(EmbeddingService embeddingService) {
            this.embeddingService = embeddingService;
            return this;
        }

        public Builder vectorStore(VectorStore vectorStore) {
            this.vectorStore = vectorStore;
            return this;
        }

        public Builder keywordSearchService(KeywordSearchService keywordSearchService) {
            this.keywordSearchService = keywordSearchService;
            return this;
        }

        public Builder promptStrategy(PromptStrategy promptStrategy) {
            this.promptStrategy = promptStrategy;
            return this;
        }

        public MemoryAgent build() {
            return new MemoryAgent(this);
        }
    }
}
