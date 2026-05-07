package com.ke.utopia.agent.memory.model;

/**
 * 记忆层级枚举。
 * CORE:     L1 - 核心记忆，始终注入 Prompt（现有 Curated Memory）
 * ARCHIVED: L2 - 归档记忆，通过语义搜索检索
 * RAW:      L3 - 原始对话记录，按需检索
 */
public enum MemoryTier {
    CORE,
    ARCHIVED,
    RAW
}
