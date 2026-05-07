package com.ke.utopia.agent.memory.memory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 记忆相关性跟踪器：跟踪检索频率，自动提升高频检索记忆的重要性评分。
 *
 * <p>每 {@link #BOOST_INTERVAL} 次检索触发一次提升，
 * 每次提升 {@link #BOOST_AMOUNT}，上限 {@link #MAX_IMPORTANCE}。</p>
 */
public final class RelevanceTracker {

    /** 触发重要性提升的检索间隔次数。 */
    static final int BOOST_INTERVAL = 5;

    /** 每次提升的量。 */
    static final double BOOST_AMOUNT = 0.05;

    /** 最大重要性评分。 */
    static final double MAX_IMPORTANCE = 1.0;

    /** entryId -> 当前周期检索计数。 */
    private final ConcurrentMap<String, Integer> retrievalCounts = new ConcurrentHashMap<>();

    /**
     * 记录一次检索并返回是否需要提升重要性。
     *
     * @param entryId 记忆条目 ID
     * @return true 表示需要提升（达到提升间隔）
     */
    public boolean recordRetrieval(String entryId) {
        Integer newCount = retrievalCounts.compute(entryId, (k, v) -> (v == null ? 0 : v) + 1);
        return newCount % BOOST_INTERVAL == 0;
    }

    /**
     * 计算提升后的重要性评分。
     *
     * @param currentScore 当前重要性评分
     * @return 提升后的评分（不超过 MAX_IMPORTANCE）
     */
    public double calculateBoostedScore(double currentScore) {
        return Math.min(currentScore + BOOST_AMOUNT, MAX_IMPORTANCE);
    }

    /**
     * 移除某个条目的跟踪数据。
     */
    public void remove(String entryId) {
        retrievalCounts.remove(entryId);
    }

    /**
     * 重置某个条目的检索计数。
     */
    public void resetCount(String entryId) {
        retrievalCounts.put(entryId, 0);
    }
}
