package com.ke.utopia.agent.memory.model;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 单轮对话上下文，聚合一站式处理用户消息后所需的所有上下文数据。
 * 调用方拿到精确数据后自行组装传递给下游应用。
 *
 * <p>通过 {@link com.ke.utopia.agent.memory.MemoryAgent#processUserMessage(String, String)}
 * 一步获取，替代之前的多步手动调用。
 */
public final class TurnContext {

    private final ConversationMessage userMessage;
    private final List<ConversationMessage> sessionMessages;
    private final MemorySnapshot memorySnapshot;
    private final UserProfile userProfile;
    private final List<IntentSummary> recentSummaries;
    private final boolean summarizationRecommended;
    private final IncrementalIntent incrementalIntent;
    private final String memoryContextPrefix;
    private final CompletableFuture<IntentSummary> pendingSummarization;

    private TurnContext(Builder builder) {
        this.userMessage = builder.userMessage;
        this.sessionMessages = Collections.unmodifiableList(builder.sessionMessages);
        this.memorySnapshot = builder.memorySnapshot;
        this.userProfile = builder.userProfile;
        this.recentSummaries = Collections.unmodifiableList(builder.recentSummaries);
        this.summarizationRecommended = builder.summarizationRecommended;
        this.incrementalIntent = builder.incrementalIntent;
        this.memoryContextPrefix = buildContextPrefix(builder);
        this.pendingSummarization = builder.pendingSummarization;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 本轮已记录的用户消息。 */
    public ConversationMessage getUserMessage() { return userMessage; }

    /** 会话的完整消息历史（含本轮已记录的用户消息）。 */
    public List<ConversationMessage> getSessionMessages() { return sessionMessages; }

    /** 会话启动时冻结的记忆快照。 */
    public MemorySnapshot getMemorySnapshot() { return memorySnapshot; }

    /** 跨会话用户画像。 */
    public UserProfile getUserProfile() { return userProfile; }

    /** 最近的意图总结列表。 */
    public List<IntentSummary> getRecentSummaries() { return recentSummaries; }

    /** 是否建议触发总结。 */
    public boolean isSummarizationRecommended() { return summarizationRecommended; }

    /**
     * 增量意图（每轮实时推导，不需要累积到触发总结阈值）。
     * 可能为 null（配置关闭或推导失败时）。
     */
    public IncrementalIntent getIncrementalIntent() { return incrementalIntent; }

    /**
     * 自动管道触发的异步总结结果（可为 null，当管道关闭或未触发时）。
     * 调用方可通过 {@code getPendingSummarization().ifPresent(f -> f.thenAccept(...))} 可选等待结果。
     */
    public Optional<CompletableFuture<IntentSummary>> getPendingSummarization() {
        return Optional.ofNullable(pendingSummarization);
    }

    /**
     * 组装好的记忆上下文前缀，包含用户画像、记忆快照、增量意图、最近意图。
     * 调用方直接拼到自己的 prompt 前即可请求 LLM。
     */
    public String getMemoryContextPrefix() { return memoryContextPrefix; }

    /**
     * 获取最近的意图总结。
     */
    public Optional<IntentSummary> getLatestSummary() {
        return recentSummaries.stream()
                .max(Comparator.comparing(IntentSummary::getCreatedAt));
    }

    @Override
    public String toString() {
        return "TurnContext{userMessage='" + userMessage.getContent() +
                "', messages=" + sessionMessages.size() +
                ", memorySnapshot=" + (memorySnapshot != null && !memorySnapshot.isEmpty()) +
                ", profile=" + (userProfile != null) +
                ", summaries=" + recentSummaries.size() +
                ", summarizeRecommended=" + summarizationRecommended + "}";
    }

    private static String buildContextPrefix(Builder builder) {
        StringBuilder sb = new StringBuilder();

        if (builder.userProfile != null && !builder.userProfile.getProfileEntries().isEmpty()) {
            sb.append("## 用户画像\n");
            for (MemoryEntry entry : builder.userProfile.getProfileEntries()) {
                sb.append("- ").append(entry.getContent()).append("\n");
            }
            sb.append("\n");
        }

        if (builder.memorySnapshot != null && !builder.memorySnapshot.isEmpty()) {
            sb.append("## 记忆上下文\n");
            sb.append(builder.memorySnapshot.getMemoryBlock()).append("\n\n");
        }

        // 增量意图（优先显示，反映最新意图）
        if (builder.incrementalIntent != null && !builder.incrementalIntent.isEmpty()) {
            sb.append("## 当前意图\n");
            if (builder.incrementalIntent.getCoreIntent() != null
                    && !builder.incrementalIntent.getCoreIntent().isEmpty()) {
                sb.append(builder.incrementalIntent.getCoreIntent()).append("\n");
            }
            if (!builder.incrementalIntent.getKeyParams().isEmpty()) {
                sb.append("关键参数: ").append(builder.incrementalIntent.getKeyParams()).append("\n");
            }
            sb.append("\n");
        }

        // 历史意图总结（如果有）
        if (!builder.recentSummaries.isEmpty()) {
            Optional<IntentSummary> latest = builder.recentSummaries.stream()
                    .max(Comparator.comparing(IntentSummary::getCreatedAt));
            latest.ifPresent(s -> {
                if (s.getCoreIntent() != null && !s.getCoreIntent().isEmpty()) {
                    sb.append("## 历史意图总结\n");
                    sb.append(s.getCoreIntent()).append("\n");
                }
            });
        }

        return sb.toString().trim();
    }

    public static class Builder {
        private ConversationMessage userMessage;
        private List<ConversationMessage> sessionMessages = Collections.emptyList();
        private MemorySnapshot memorySnapshot = MemorySnapshot.empty();
        private UserProfile userProfile;
        private List<IntentSummary> recentSummaries = Collections.emptyList();
        private boolean summarizationRecommended;
        private IncrementalIntent incrementalIntent;
        private CompletableFuture<IntentSummary> pendingSummarization;

        public Builder userMessage(ConversationMessage userMessage) {
            this.userMessage = userMessage;
            return this;
        }

        public Builder sessionMessages(List<ConversationMessage> sessionMessages) {
            this.sessionMessages = sessionMessages;
            return this;
        }

        public Builder memorySnapshot(MemorySnapshot memorySnapshot) {
            this.memorySnapshot = memorySnapshot;
            return this;
        }

        public Builder userProfile(UserProfile userProfile) {
            this.userProfile = userProfile;
            return this;
        }

        public Builder recentSummaries(List<IntentSummary> recentSummaries) {
            this.recentSummaries = recentSummaries;
            return this;
        }

        public Builder summarizationRecommended(boolean summarizationRecommended) {
            this.summarizationRecommended = summarizationRecommended;
            return this;
        }

        public Builder incrementalIntent(IncrementalIntent incrementalIntent) {
            this.incrementalIntent = incrementalIntent;
            return this;
        }

        public Builder pendingSummarization(CompletableFuture<IntentSummary> pendingSummarization) {
            this.pendingSummarization = pendingSummarization;
            return this;
        }

        public TurnContext build() {
            Objects.requireNonNull(userMessage, "userMessage must not be null");
            return new TurnContext(this);
        }
    }
}
