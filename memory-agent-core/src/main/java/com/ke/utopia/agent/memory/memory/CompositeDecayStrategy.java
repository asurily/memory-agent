package com.ke.utopia.agent.memory.memory;

import com.ke.utopia.agent.memory.model.MemoryEntry;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 综合评分策略：结合时间衰减、访问频率和最近访问时间。
 * score = timeWeight * timeScore + frequencyWeight * frequencyScore + recencyWeight * recencyScore
 */
public final class CompositeDecayStrategy implements MemoryDecayStrategy {

    private final double timeWeight;
    private final double frequencyWeight;
    private final double recencyWeight;
    private final int halfLifeDays;

    public CompositeDecayStrategy(int halfLifeDays) {
        this(halfLifeDays, 0.4, 0.3, 0.3);
    }

    public CompositeDecayStrategy(int halfLifeDays, double timeWeight, double frequencyWeight, double recencyWeight) {
        this.halfLifeDays = halfLifeDays;
        this.timeWeight = timeWeight;
        this.frequencyWeight = frequencyWeight;
        this.recencyWeight = recencyWeight;
    }

    @Override
    public double calculateScore(MemoryEntry entry, Instant now) {
        double timeScore = calculateTimeScore(entry, now);
        double frequencyScore = calculateFrequencyScore(entry);
        double recencyScore = calculateRecencyScore(entry, now);

        return timeWeight * timeScore + frequencyWeight * frequencyScore + recencyWeight * recencyScore;
    }

    private double calculateTimeScore(MemoryEntry entry, Instant now) {
        long daysSinceLastAccess = ChronoUnit.DAYS.between(entry.getLastAccessedAt(), now);
        double lambda = Math.log(2) / halfLifeDays;
        return entry.getImportanceScore() * Math.exp(-lambda * daysSinceLastAccess);
    }

    private double calculateFrequencyScore(MemoryEntry entry) {
        // Normalize: 0 accesses -> 0.0, 10+ accesses -> 1.0
        return Math.min(1.0, entry.getAccessCount() / 10.0);
    }

    private double calculateRecencyScore(MemoryEntry entry, Instant now) {
        long hoursSinceLastAccess = ChronoUnit.HOURS.between(entry.getLastAccessedAt(), now);
        // Exponential decay with 24-hour half-life for recency
        return Math.exp(-Math.log(2) * hoursSinceLastAccess / 24.0);
    }

    @Override
    public String getName() {
        return "composite-decay(halfLife=" + halfLifeDays + "d, tw=" + timeWeight + ", fw=" + frequencyWeight + ", rw=" + recencyWeight + ")";
    }
}
