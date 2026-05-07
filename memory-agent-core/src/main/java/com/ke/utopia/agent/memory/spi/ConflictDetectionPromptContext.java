package com.ke.utopia.agent.memory.spi;

import com.ke.utopia.agent.memory.model.MemoryEntry;

import java.util.List;

/**
 * 冲突检测 prompt 的上下文参数。
 */
public final class ConflictDetectionPromptContext {

    private final String newContent;
    private final List<MemoryEntry> existingMemories;

    public ConflictDetectionPromptContext(String newContent, List<MemoryEntry> existingMemories) {
        this.newContent = newContent;
        this.existingMemories = existingMemories;
    }

    public String getNewContent() { return newContent; }
    public List<MemoryEntry> getExistingMemories() { return existingMemories; }
}
