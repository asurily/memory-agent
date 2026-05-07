package com.ke.utopia.agent.memory.model;

/**
 * 记忆冲突检测结果。
 */
public final class ConflictResolution {

    public enum ConflictType {
        CONTRADICT,  // 新旧记忆矛盾
        SUPPLEMENT,  // 新记忆补充旧记忆
        DUPLICATE,   // 新记忆与旧记忆重复
        UPDATE       // 新记忆更新旧记忆
    }

    public enum ResolutionAction {
        REPLACE_OLD,  // 替换旧记忆
        MERGE,        // 合并新旧记忆
        KEEP_BOTH,    // 两者都保留
        DISCARD_NEW   // 丢弃新记忆
    }

    private final ConflictType conflictType;
    private final ResolutionAction resolution;
    private final String mergedContent;
    private final double confidence;
    private final String explanation;
    private final String replacedEntryId;

    public ConflictResolution(ConflictType conflictType, ResolutionAction resolution,
                               String mergedContent, double confidence, String explanation) {
        this(conflictType, resolution, mergedContent, confidence, explanation, null);
    }

    public ConflictResolution(ConflictType conflictType, ResolutionAction resolution,
                               String mergedContent, double confidence, String explanation,
                               String replacedEntryId) {
        this.conflictType = conflictType;
        this.resolution = resolution;
        this.mergedContent = mergedContent;
        this.confidence = confidence;
        this.explanation = explanation;
        this.replacedEntryId = replacedEntryId;
    }

    public ConflictType getConflictType() { return conflictType; }
    public ResolutionAction getResolution() { return resolution; }
    public String getMergedContent() { return mergedContent; }
    public double getConfidence() { return confidence; }
    public String getExplanation() { return explanation; }
    public String getReplacedEntryId() { return replacedEntryId; }

    @Override
    public String toString() {
        return "ConflictResolution{type=" + conflictType + ", action=" + resolution +
                ", confidence=" + confidence + ", replacedEntryId='" + replacedEntryId + "'}";
    }
}
