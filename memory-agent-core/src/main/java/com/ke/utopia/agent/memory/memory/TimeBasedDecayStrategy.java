package com.ke.utopia.agent.memory.memory;

import com.ke.utopia.agent.memory.model.MemoryEntry;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 基于时间的指数衰减策略。
 * score = importanceScore * e^(-lambda * daysSinceLastAccess)
 * lambda = ln(2) / halfLifeDays
 */
public final class TimeBasedDecayStrategy implements MemoryDecayStrategy {

    private final int halfLifeDays;
    private final double lambda;

    public TimeBasedDecayStrategy(int halfLifeDays) {
        this.halfLifeDays = halfLifeDays;
        this.lambda = Math.log(2) / halfLifeDays;
    }

    @Override
    public double calculateScore(MemoryEntry entry, Instant now) {
        long daysSinceLastAccess = ChronoUnit.DAYS.between(entry.getLastAccessedAt(), now);
        double decayFactor = Math.exp(-lambda * daysSinceLastAccess);
        return entry.getImportanceScore() * decayFactor;
    }

    @Override
    public String getName() {
        return "time-based-decay(halfLife=" + halfLifeDays + "d)";
    }
}
