package com.ke.utopia.agent.memory.model;

import java.time.Instant;

/**
 * 冻结快照（会话启动时捕获，会话期间不可变）。
 * 类比 Hermes 的 _system_prompt_snapshot，保护 LLM Prefix Cache。
 */
public final class MemorySnapshot {

    private final String memoryBlock;
    private final String userProfileBlock;
    private final Instant capturedAt;

    public MemorySnapshot(String memoryBlock, String userProfileBlock, Instant capturedAt) {
        this.memoryBlock = memoryBlock;
        this.userProfileBlock = userProfileBlock;
        this.capturedAt = capturedAt;
    }

    public static MemorySnapshot of(String memoryBlock, String userProfileBlock) {
        return new MemorySnapshot(memoryBlock, userProfileBlock, Instant.now());
    }

    public static MemorySnapshot empty() {
        return new MemorySnapshot("", "", Instant.now());
    }

    public boolean isEmpty() {
        return (memoryBlock == null || memoryBlock.isEmpty()) &&
                (userProfileBlock == null || userProfileBlock.isEmpty());
    }

    public String getMemoryBlock() { return memoryBlock; }
    public String getUserProfileBlock() { return userProfileBlock; }
    public Instant getCapturedAt() { return capturedAt; }

    @Override
    public String toString() {
        return "MemorySnapshot{capturedAt=" + capturedAt +
                ", memoryBlockLen=" + (memoryBlock != null ? memoryBlock.length() : 0) +
                ", userProfileBlockLen=" + (userProfileBlock != null ? userProfileBlock.length() : 0) + "}";
    }
}
