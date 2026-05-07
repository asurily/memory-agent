package com.ke.utopia.agent.memory.spi;

import com.ke.utopia.agent.memory.model.*;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * LLM 意图总结 SPI。
 * 用户实现此接口对接 OpenAI / Claude / 其他 LLM API。
 */
public interface IntentSummarizer {

    /**
     * 同步总结对话消息为结构化意图摘要。
     */
    IntentSummary summarize(String userId, String sessionId,
                            List<ConversationMessage> messages,
                            Optional<UserProfile> userProfile,
                            Optional<List<IntentSummary>> previousSummaries);

    /**
     * 异步版本。
     */
    CompletableFuture<IntentSummary> summarizeAsync(String userId, String sessionId,
                                                     List<ConversationMessage> messages,
                                                     Optional<UserProfile> userProfile,
                                                     Optional<List<IntentSummary>> previousSummaries);

    /**
     * 压缩对话片段为紧凑摘要（上下文压缩用）。
     */
    String compressConversation(List<ConversationMessage> messages, int targetTokenBudget);

    /**
     * 返回使用的 LLM 模型标识。
     */
    String getModelIdentifier();

    /**
     * 从对话消息中提取值得记忆的信息。
     * 默认空实现，不影响现有 SPI 实现。
     */
    default List<MemoryExtraction> extractMemories(List<ConversationMessage> messages,
                                                    Optional<UserProfile> existingProfile) {
        return Collections.emptyList();
    }

    /**
     * 检测新记忆内容与已有记忆之间是否存在冲突。
     * 默认返回 SUPPLEMENT / KEEP_BOTH，不做真正的冲突检测。
     */
    default ConflictResolution detectConflict(String newContent, List<MemoryEntry> existing) {
        return new ConflictResolution(ConflictResolution.ConflictType.SUPPLEMENT,
                ConflictResolution.ResolutionAction.KEEP_BOTH, null, 1.0,
                "Default: no conflict detection");
    }

    /**
     * 提取增量意图（每轮对话实时推导）。
     * <p>
     * 与 summarize() 不同，此方法适用于早期轮次（第 1-9 轮），
     * 不需要累积大量消息即可触发，能够处理上下文省略等复杂场景。
     * <p>
     * 默认实现返回空，表示不支持增量意图提取。
     *
     * @param currentMessage 当前用户消息
     * @param recentMessages 最近几轮对话（用于上下文理解）
     * @param previousIntent 上一轮意图（可能为空）
     * @param memorySnapshot 记忆快照
     * @return 增量意图，如果无法提取返回 null
     */
    default IncrementalIntentResult extractIncrementalIntent(
            ConversationMessage currentMessage,
            List<ConversationMessage> recentMessages,
            IncrementalIntent previousIntent,
            MemorySnapshot memorySnapshot) {
        return null; // 默认不支持，需要实现类提供
    }

    /**
     * 同步调用 LLM 进行对话。
     * 用于 Web UI 的对话功能。
     * 默认抛出异常，需要实现类提供具体实现。
     */
    default String callChat(List<ConversationMessage> messages,
                            Optional<String> systemPrompt) {
        throw new UnsupportedOperationException("callChat not implemented");
    }
}
