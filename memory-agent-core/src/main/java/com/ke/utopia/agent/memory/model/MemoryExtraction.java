package com.ke.utopia.agent.memory.model;

import java.util.Objects;

/**
 * 记忆提取结果值对象。
 */
public final class MemoryExtraction {

    private final String content;
    private final MemoryType type;
    private final double confidence;
    private final String category;
    private final double importanceScore;

    public MemoryExtraction(String content, MemoryType type, double confidence, String category) {
        this(content, type, confidence, category, 0.5);
    }

    public MemoryExtraction(String content, MemoryType type, double confidence, String category,
                             double importanceScore) {
        this.content = Objects.requireNonNull(content, "content must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.confidence = confidence;
        this.category = category != null ? category : "general";
        this.importanceScore = importanceScore;
    }

    public String getContent() { return content; }
    public MemoryType getType() { return type; }
    public double getConfidence() { return confidence; }
    public String getCategory() { return category; }
    public double getImportanceScore() { return importanceScore; }

    @Override
    public String toString() {
        return "MemoryExtraction{type=" + type + ", confidence=" + confidence +
                ", importance=" + importanceScore +
                ", category='" + category + "', content='" +
                (content.length() > 60 ? content.substring(0, 60) + "..." : content) + "'}";
    }
}
