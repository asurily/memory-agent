package com.ke.utopia.agent.memory.spi.defaults;

import com.ke.utopia.agent.memory.model.VectorSearchResult;
import com.ke.utopia.agent.memory.spi.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 基于内存的向量存储实现。
 * 搜索时遍历计算余弦相似度，适用于开发测试，不依赖外部服务。
 */
public final class InMemoryVectorStore implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryVectorStore.class);

    private final Map<String, VectorEntry> entries = new ConcurrentHashMap<>();

    @Override
    public void initialize() {
        log.info("InMemoryVectorStore initialized");
    }

    @Override
    public void shutdown() {
        entries.clear();
        log.info("InMemoryVectorStore shut down");
    }

    @Override
    public String upsert(String id, float[] embedding, Map<String, String> metadata) {
        entries.put(id, new VectorEntry(id, embedding, metadata.getOrDefault("content", ""), metadata));
        return id;
    }

    @Override
    public void delete(String id) {
        entries.remove(id);
    }

    @Override
    public List<VectorSearchResult> search(float[] queryEmbedding, int topK, Map<String, String> filter) {
        return entries.values().stream()
                .filter(entry -> matchesFilter(entry, filter))
                .map(entry -> new VectorSearchResult(
                        entry.id,
                        cosineSimilarity(queryEmbedding, entry.embedding),
                        entry.content,
                        entry.metadata))
                .sorted(Comparator.comparingDouble(VectorSearchResult::getScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    private boolean matchesFilter(VectorEntry entry, Map<String, String> filter) {
        if (filter == null || filter.isEmpty()) return true;
        for (Map.Entry<String, String> f : filter.entrySet()) {
            String value = entry.metadata.get(f.getKey());
            if (!f.getValue().equals(value)) return false;
        }
        return true;
    }

    private static double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) return 0.0;
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private static class VectorEntry {
        final String id;
        final float[] embedding;
        final String content;
        final Map<String, String> metadata;

        VectorEntry(String id, float[] embedding, String content, Map<String, String> metadata) {
            this.id = id;
            this.embedding = embedding;
            this.content = content;
            this.metadata = metadata;
        }
    }
}
