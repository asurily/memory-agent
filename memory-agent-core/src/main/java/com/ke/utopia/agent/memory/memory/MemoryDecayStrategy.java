package com.ke.utopia.agent.memory.memory;

import com.ke.utopia.agent.memory.model.MemoryEntry;

import java.time.Instant;

/**
 * 记忆衰退评分策略接口。
 */
public interface MemoryDecayStrategy {

    /**
     * 计算记忆的当前活跃度评分。
     * @param entry 记忆条目
     * @param now   当前时间
     * @return 活跃度评分 (0.0-1.0)
     */
    double calculateScore(MemoryEntry entry, Instant now);

    /**
     * 返回策略名称。
     */
    String getName();
}
