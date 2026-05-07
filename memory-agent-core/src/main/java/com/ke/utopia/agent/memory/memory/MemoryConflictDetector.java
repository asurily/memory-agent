package com.ke.utopia.agent.memory.memory;

import com.ke.utopia.agent.memory.model.ConflictResolution;
import com.ke.utopia.agent.memory.model.MemoryEntry;
import com.ke.utopia.agent.memory.spi.IntentSummarizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 记忆冲突检测器：编排冲突检测流程。
 * 通过 LLM 判断新记忆内容与已有记忆之间是否存在矛盾。
 */
public final class MemoryConflictDetector {

    private static final Logger log = LoggerFactory.getLogger(MemoryConflictDetector.class);

    private final IntentSummarizer summarizer;

    public MemoryConflictDetector(IntentSummarizer summarizer) {
        this.summarizer = summarizer;
    }

    /**
     * 检测新记忆内容与已有记忆之间是否存在冲突。
     *
     * @param newContent      新记忆内容
     * @param existingEntries 已有的记忆条目
     * @return 冲突检测结果
     */
    public ConflictResolution detect(String newContent, List<MemoryEntry> existingEntries) {
        if (existingEntries == null || existingEntries.isEmpty()) {
            return new ConflictResolution(ConflictResolution.ConflictType.SUPPLEMENT,
                    ConflictResolution.ResolutionAction.KEEP_BOTH, null, 1.0,
                    "No existing memories to conflict with");
        }

        try {
            ConflictResolution resolution = summarizer.detectConflict(newContent, existingEntries);
            log.debug("Conflict detection result: type={}, action={}, confidence={}",
                    resolution.getConflictType(), resolution.getResolution(), resolution.getConfidence());
            return resolution;
        } catch (Exception e) {
            log.warn("Conflict detection failed, defaulting to KEEP_BOTH: {}", e.getMessage());
            return new ConflictResolution(ConflictResolution.ConflictType.SUPPLEMENT,
                    ConflictResolution.ResolutionAction.KEEP_BOTH, null, 0.5,
                    "Conflict detection failed: " + e.getMessage());
        }
    }
}
