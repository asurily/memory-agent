package com.ke.utopia.agent.memory.memory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 轻量级性能指标收集器。
 * 基于 LongAdder 和 ConcurrentHashMap，零外部依赖。
 */
public final class MemoryMetricsCollector {

    // --- 计数器 ---
    private final LongAdder addMemoryCount = new LongAdder();
    private final LongAdder searchCount = new LongAdder();
    private final LongAdder summarizeCount = new LongAdder();
    private final LongAdder decayRunCount = new LongAdder();
    private final LongAdder memoryExtractionCount = new LongAdder();
    private final LongAdder errorCount = new LongAdder();

    // --- Auto Pipeline ---
    private final LongAdder autoSummarizeCount = new LongAdder();
    private final LongAdder autoCompressCount = new LongAdder();

    // --- 延迟累加（纳秒） ---
    private final LongAdder addMemoryLatencyNs = new LongAdder();
    private final LongAdder searchLatencyNs = new LongAdder();
    private final LongAdder summarizeLatencyNs = new LongAdder();
    private final LongAdder decayRunLatencyNs = new LongAdder();

    // --- 命中率 ---
    private final LongAdder searchHitCount = new LongAdder();
    private final LongAdder searchMissCount = new LongAdder();

    // --- 冲突分布 ---
    private final ConcurrentMap<String, LongAdder> conflictDistribution = new ConcurrentHashMap<>();

    // ========== Record Methods ==========

    public void recordAddMemory(long latencyNs) {
        addMemoryCount.increment();
        addMemoryLatencyNs.add(latencyNs);
    }

    public void recordSearch(long latencyNs, int resultCount) {
        searchCount.increment();
        searchLatencyNs.add(latencyNs);
        if (resultCount > 0) {
            searchHitCount.increment();
        } else {
            searchMissCount.increment();
        }
    }

    public void recordSummarize(long latencyNs) {
        summarizeCount.increment();
        summarizeLatencyNs.add(latencyNs);
    }

    public void recordDecayRun(long latencyNs) {
        decayRunCount.increment();
        decayRunLatencyNs.add(latencyNs);
    }

    public void recordMemoryExtraction() {
        memoryExtractionCount.increment();
    }

    public void recordConflictDetection(String resolutionAction) {
        conflictDistribution.computeIfAbsent(resolutionAction, k -> new LongAdder()).increment();
    }

    public void recordError() {
        errorCount.increment();
    }

    public void recordAutoSummarize() {
        autoSummarizeCount.increment();
    }

    public void recordAutoCompress() {
        autoCompressCount.increment();
    }

    // ========== Snapshot ==========

    public MetricsSnapshot snapshot() {
        return new MetricsSnapshot(
                addMemoryCount.sum(),
                searchCount.sum(),
                summarizeCount.sum(),
                decayRunCount.sum(),
                memoryExtractionCount.sum(),
                errorCount.sum(),
                addMemoryLatencyNs.sum(),
                searchLatencyNs.sum(),
                summarizeLatencyNs.sum(),
                decayRunLatencyNs.sum(),
                searchHitCount.sum(),
                searchMissCount.sum(),
                toLongMap(conflictDistribution),
                autoSummarizeCount.sum(),
                autoCompressCount.sum()
        );
    }

    private static Map<String, Long> toLongMap(Map<String, LongAdder> source) {
        Map<String, Long> result = new HashMap<>();
        for (Map.Entry<String, LongAdder> entry : source.entrySet()) {
            result.put(entry.getKey(), entry.getValue().sum());
        }
        return result;
    }

    public void reset() {
        addMemoryCount.reset();
        searchCount.reset();
        summarizeCount.reset();
        decayRunCount.reset();
        memoryExtractionCount.reset();
        errorCount.reset();
        addMemoryLatencyNs.reset();
        searchLatencyNs.reset();
        summarizeLatencyNs.reset();
        decayRunLatencyNs.reset();
        searchHitCount.reset();
        searchMissCount.reset();
        conflictDistribution.clear();
        autoSummarizeCount.reset();
        autoCompressCount.reset();
    }

    // ========== Snapshot Value Object ==========

    public static final class MetricsSnapshot {
        private final long addMemoryCount;
        private final long searchCount;
        private final long summarizeCount;
        private final long decayRunCount;
        private final long memoryExtractionCount;
        private final long errorCount;
        private final long addMemoryLatencyNs;
        private final long searchLatencyNs;
        private final long summarizeLatencyNs;
        private final long decayRunLatencyNs;
        private final long searchHitCount;
        private final long searchMissCount;
        private final Map<String, Long> conflictDistribution;
        private final long autoSummarizeCount;
        private final long autoCompressCount;

        public MetricsSnapshot(long addMemoryCount, long searchCount,
                                long summarizeCount, long decayRunCount,
                                long memoryExtractionCount, long errorCount,
                                long addMemoryLatencyNs, long searchLatencyNs,
                                long summarizeLatencyNs, long decayRunLatencyNs,
                                long searchHitCount, long searchMissCount,
                                Map<String, Long> conflictDistribution,
                                long autoSummarizeCount, long autoCompressCount) {
            this.addMemoryCount = addMemoryCount;
            this.searchCount = searchCount;
            this.summarizeCount = summarizeCount;
            this.decayRunCount = decayRunCount;
            this.memoryExtractionCount = memoryExtractionCount;
            this.errorCount = errorCount;
            this.addMemoryLatencyNs = addMemoryLatencyNs;
            this.searchLatencyNs = searchLatencyNs;
            this.summarizeLatencyNs = summarizeLatencyNs;
            this.decayRunLatencyNs = decayRunLatencyNs;
            this.searchHitCount = searchHitCount;
            this.searchMissCount = searchMissCount;
            this.conflictDistribution = Collections.unmodifiableMap(new HashMap<>(conflictDistribution));
            this.autoSummarizeCount = autoSummarizeCount;
            this.autoCompressCount = autoCompressCount;
        }

        // --- Counts ---

        public long getAddMemoryCount() { return addMemoryCount; }
        public long getSearchCount() { return searchCount; }
        public long getSummarizeCount() { return summarizeCount; }
        public long getDecayRunCount() { return decayRunCount; }
        public long getMemoryExtractionCount() { return memoryExtractionCount; }
        public long getErrorCount() { return errorCount; }

        // --- Latency (ms) ---

        public double getAddMemoryAvgLatencyMs() {
            return avgMs(addMemoryLatencyNs, addMemoryCount);
        }

        public double getSearchAvgLatencyMs() {
            return avgMs(searchLatencyNs, searchCount);
        }

        public double getSummarizeAvgLatencyMs() {
            return avgMs(summarizeLatencyNs, summarizeCount);
        }

        public double getDecayRunAvgLatencyMs() {
            return avgMs(decayRunLatencyNs, decayRunCount);
        }

        // --- Hit Rate ---

        public double getSearchHitRate() {
            long total = searchHitCount + searchMissCount;
            return total > 0 ? (double) searchHitCount / total : 0.0;
        }

        public long getSearchHitCount() { return searchHitCount; }
        public long getSearchMissCount() { return searchMissCount; }

        // --- Conflict Distribution ---

        public Map<String, Long> getConflictDistribution() {
            return conflictDistribution;
        }

        // --- Auto Pipeline ---

        public long getAutoSummarizeCount() { return autoSummarizeCount; }
        public long getAutoCompressCount() { return autoCompressCount; }

        private double avgMs(long totalNs, long count) {
            return count > 0 ? (totalNs / 1_000_000.0) / count : 0.0;
        }
    }
}
