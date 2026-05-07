package com.ke.utopia.agent.memory.spi.defaults;

import com.ke.utopia.agent.memory.model.VectorSearchResult;
import com.ke.utopia.agent.memory.spi.KeywordSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * 基于 BM25 算法的内存关键词搜索服务。
 *
 * <p>BM25 参数：k1=1.2, b=0.75。
 * 线程安全：使用 ConcurrentHashMap。
 */
public final class InMemoryKeywordSearchService implements KeywordSearchService {

    private static final Logger log = LoggerFactory.getLogger(InMemoryKeywordSearchService.class);

    /** BM25 参数 */
    private static final double K1 = 1.2;
    private static final double B = 0.75;

    /** userId -> (docId -> content) */
    private final ConcurrentMap<String, ConcurrentMap<String, String>> userDocuments = new ConcurrentHashMap<>();

    /** userId -> (term -> (docId -> term frequency in doc)) */
    private final ConcurrentMap<String, ConcurrentMap<String, ConcurrentMap<String, Integer>>> userTermFreq = new ConcurrentHashMap<>();

    /** userId -> (term -> document frequency across all docs for user) */
    private final ConcurrentMap<String, ConcurrentMap<String, Integer>> userDocFreq = new ConcurrentHashMap<>();

    /** 分词缓存 */
    private static final int MIN_TERM_LENGTH = 1;

    @Override
    public void initialize() {
        log.info("InMemoryKeywordSearchService initialized");
    }

    @Override
    public void shutdown() {
        userDocuments.clear();
        userTermFreq.clear();
        userDocFreq.clear();
        log.info("InMemoryKeywordSearchService shut down");
    }

    @Override
    public List<VectorSearchResult> search(String query, int topK, String userId) {
        ConcurrentMap<String, String> docs = userDocuments.get(userId);
        if (docs == null || docs.isEmpty()) {
            return Collections.emptyList();
        }

        String[] queryTerms = tokenize(query);
        if (queryTerms.length == 0) {
            return Collections.emptyList();
        }

        ConcurrentMap<String, ConcurrentMap<String, Integer>> termFreq = userTermFreq.getOrDefault(userId, new ConcurrentHashMap<>());
        ConcurrentMap<String, Integer> docFreq = userDocFreq.getOrDefault(userId, new ConcurrentHashMap<>());

        int totalDocs = docs.size();
        double avgDocLen = docs.values().stream().mapToInt(String::length).average().orElse(1.0);

        // Calculate BM25 score for each document
        Map<String, Double> scores = new HashMap<>();
        for (Map.Entry<String, String> docEntry : docs.entrySet()) {
            String docId = docEntry.getKey();
            String content = docEntry.getValue();
            int docLen = content.length();

            double score = 0.0;
            for (String term : queryTerms) {
                int tf = termFreq.getOrDefault(term, new ConcurrentHashMap<String, Integer>()).getOrDefault(docId, 0);
                if (tf == 0) continue;

                int df = docFreq.getOrDefault(term, 0);
                double idf = Math.log((totalDocs - df + 0.5) / (df + 0.5) + 1.0);

                double numerator = tf * (K1 + 1);
                double denominator = tf + K1 * (1 - B + B * docLen / avgDocLen);
                score += idf * numerator / denominator;
            }

            if (score > 0) {
                scores.put(docId, score);
            }
        }

        // Normalize scores to [0, 1] and return sorted results
        double maxScore = scores.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);

        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> {
                    String docId = e.getKey();
                    String content = docs.get(docId);
                    double normalizedScore = maxScore > 0 ? e.getValue() / maxScore : 0;
                    Map<String, String> metadata = new HashMap<>();
                    metadata.put("userId", userId);
                    metadata.put("content", content);
                    return new VectorSearchResult(docId, normalizedScore, content, metadata);
                })
                .collect(Collectors.toList());
    }

    @Override
    public void index(String id, String content, String userId) {
        userDocuments.computeIfAbsent(userId, k -> new ConcurrentHashMap<>()).put(id, content);

        String[] terms = tokenize(content);
        ConcurrentMap<String, ConcurrentMap<String, Integer>> tf = userTermFreq.computeIfAbsent(userId, k -> new ConcurrentHashMap<>());

        // Count unique terms for this doc (for doc frequency)
        Set<String> uniqueTerms = new HashSet<>();

        for (String term : terms) {
            tf.computeIfAbsent(term, k -> new ConcurrentHashMap<>())
                    .merge(id, 1, Integer::sum);
            uniqueTerms.add(term);
        }

        // Update document frequency
        ConcurrentMap<String, Integer> df = userDocFreq.computeIfAbsent(userId, k -> new ConcurrentHashMap<>());
        for (String term : uniqueTerms) {
            df.merge(term, 1, Integer::sum);
        }
    }

    @Override
    public void remove(String id) {
        for (ConcurrentMap.Entry<String, ConcurrentMap<String, String>> userEntry : userDocuments.entrySet()) {
            String userId = userEntry.getKey();
            ConcurrentMap<String, String> docs = userEntry.getValue();
            String removedContent = docs.remove(id);
            if (removedContent != null) {
                // Remove term frequency entries for this document
                String[] terms = tokenize(removedContent);
                ConcurrentMap<String, ConcurrentMap<String, Integer>> tf = userTermFreq.get(userId);
                Set<String> uniqueTerms = new HashSet<>(Arrays.asList(terms));

                if (tf != null) {
                    for (String term : uniqueTerms) {
                        ConcurrentMap<String, Integer> docMap = tf.get(term);
                        if (docMap != null) {
                            docMap.remove(id);
                            if (docMap.isEmpty()) {
                                tf.remove(term);
                            }
                        }
                    }
                }

                // Update document frequency
                ConcurrentMap<String, Integer> df = userDocFreq.get(userId);
                if (df != null) {
                    for (String term : uniqueTerms) {
                        df.computeIfPresent(term, (k, v) -> v > 1 ? v - 1 : null);
                    }
                }
            }
        }
    }

    /**
     * 简单的分词实现：按非字母数字字符分割，转小写。
     */
    private String[] tokenize(String text) {
        if (text == null || text.isEmpty()) {
            return new String[0];
        }
        return text.toLowerCase(Locale.ROOT)
                .split("[^\\p{L}\\p{N}]+");
    }
}
