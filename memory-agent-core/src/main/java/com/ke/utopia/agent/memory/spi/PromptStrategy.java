package com.ke.utopia.agent.memory.spi;

/**
 * Prompt 构建 SPI 接口，将 prompt 构建从 LLM 调用中解耦。
 *
 * <p>不同应用场景（医疗、客服、编程等）可提供自定义实现，
 * 控制意图总结、上下文压缩、记忆提取、冲突检测、增量意图 5 处 prompt 的内容。</p>
 *
 * <p>默认实现 {@link com.ke.utopia.agent.memory.spi.defaults.DefaultPromptStrategy} 从
 * {@link com.ke.utopia.agent.memory.spi.defaults.OpenAIIntentSummarizer} 中原样提取 prompt 逻辑，
 * 保证不提供自定义实现时行为完全一致。</p>
 */
public interface PromptStrategy {

    /**
     * 构建意图总结 prompt。
     *
     * @param context 总结上下文（userId, sessionId, messages, userProfile, previousSummaries）
     * @return PromptTemplate 包含可选 system message 和必选 user message
     */
    PromptTemplate buildSummarizePrompt(SummarizePromptContext context);

    /**
     * 构建上下文压缩 prompt。
     *
     * @param context 压缩上下文（messages, targetTokenBudget）
     * @return PromptTemplate 包含可选 system message 和必选 user message
     */
    PromptTemplate buildCompressPrompt(CompressPromptContext context);

    /**
     * 构建记忆提取 prompt。
     *
     * @param context 提取上下文（messages, existingProfile）
     * @return PromptTemplate 包含可选 system message 和必选 user message
     */
    PromptTemplate buildMemoryExtractionPrompt(MemoryExtractionPromptContext context);

    /**
     * 构建冲突检测 prompt。
     *
     * @param context 冲突检测上下文（newContent, existingMemories）
     * @return PromptTemplate 包含可选 system message 和必选 user message
     */
    PromptTemplate buildConflictDetectionPrompt(ConflictDetectionPromptContext context);

    /**
     * 构建增量意图提取 prompt。
     *
     * @param context 增量意图上下文（currentMessage, recentMessages, previousIntent, memorySnapshot）
     * @return PromptTemplate 包含可选 system message 和必选 user message
     */
    PromptTemplate buildIncrementalIntentPrompt(IncrementalIntentPromptContext context);
}
