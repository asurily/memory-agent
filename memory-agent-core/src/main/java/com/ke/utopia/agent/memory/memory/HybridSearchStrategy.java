package com.ke.utopia.agent.memory.memory;

import com.ke.utopia.agent.memory.model.VectorSearchResult;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 混合搜索策略：使用 Reciprocal Rank Fusion (RRF) 融合向量搜索和关键词搜索结果。
 *
 * <p>RRF score = Σ(1 / (k + rank_i))
 * 默认 k=60。
 */
public final class HybridSearchStrategy {

    private HybridSearchStrategy() {}

    /**
     * 使用 RRF 算法融合向量搜索结果和关键词搜索结果。
     *
     * @param vectorResults  向量搜索结果
     * @param keywordResults 关键词搜索结果
     * @param topK           返回 topK 条结果
     * @param k              RRF 常数（默认 60）
     * @return 融合后的搜索结果，按 RRF score 降序排列
     */
    public static List<VectorSearchResult> rrf(
            List<VectorSearchResult> vectorResults,
            List<VectorSearchResult> keywordResults,
            int topK,
            int k) {

        if (vectorResults == null || vectorResults.isEmpty()) {
            return keywordResults != null ? keywordResults.stream().limit(topK).collect(Collectors.toList()) : Collections.emptyList();
        }
        if (keywordResults == null || keywordResults.isEmpty()) {
            return vectorResults.stream().limit(topK).collect(Collectors.toList());
        }

        // Build rank maps: id -> position (1-indexed)
        Map<String, Integer> vectorRank = buildRankMap(vectorResults);
        Map<String, Integer> keywordRank = buildRankMap(keywordResults);

        // Collect all unique IDs
        Set<String> allIds = new HashSet<>();
        allIds.addAll(vectorRank.keySet());
        allIds.addAll(keywordRank.keySet());

        // Calculate RRF scores
        Map<String, Double> rrfScores = new HashMap<>();
        Map<String, VectorSearchResult> resultMap = new HashMap<>();

        for (VectorSearchResult r : vectorResults) {
            resultMap.put(r.getId(), r);
        }
        for (VectorSearchResult r : keywordResults) {
            // Prefer vector result if exists (has content/metadata)
            if (!resultMap.containsKey(r.getId())) {
                resultMap.put(r.getId(), r);
            }
        }

        for (String id : allIds) {
            double score = 0.0;
            Integer vRank = vectorRank.get(id);
            Integer kRank = keywordRank.get(id);

            if (vRank != null) {
                score += 1.0 / (k + vRank);
            }
            if (kRank != null) {
                score += 1.0 / (k + kRank);
            }

            rrfScores.put(id, score);
        }

        // Sort by RRF score descending, limit to topK
        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> {
                    VectorSearchResult original = resultMap.get(e.getKey());
                    // Override score with RRF score for proper ranking
                    return new VectorSearchResult(
                            original.getId(),
                            e.getValue(),
                            original.getContent(),
                            original.getMetadata()
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * 构建 1-indexed 排名映射。
     */
    private static Map<String, Integer> buildRankMap(List<VectorSearchResult> results) {
        Map<String, Integer> rank = new HashMap<>();
        for (int i = 0; i < results.size(); i++) {
            rank.put(results.get(i).getId(), i + 1);
        }
        return rank;
    }
}
