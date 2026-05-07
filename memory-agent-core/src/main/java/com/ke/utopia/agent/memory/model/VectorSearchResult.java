package com.ke.utopia.agent.memory.model;

import java.util.Collections;
import java.util.Map;

/**
 * 向量搜索结果。
 */
public final class VectorSearchResult {

    private final String id;
    private final double score;
    private final String content;
    private final Map<String, String> metadata;

    public VectorSearchResult(String id, double score, String content, Map<String, String> metadata) {
        this.id = id;
        this.score = score;
        this.content = content;
        this.metadata = metadata != null ? Collections.unmodifiableMap(metadata) : Collections.emptyMap();
    }

    public String getId() { return id; }
    public double getScore() { return score; }
    public String getContent() { return content; }
    public Map<String, String> getMetadata() { return metadata; }

    @Override
    public String toString() {
        return "VectorSearchResult{id='" + id + "', score=" + String.format("%.4f", score) +
                ", content='" + (content != null && content.length() > 50 ? content.substring(0, 50) + "..." : content) + "'}";
    }
}
