package com.ke.utopia.agent.memory.compression;

/**
 * 上下文压缩配置。
 */
public final class CompressionConfig {

    private final double compressionThresholdRatio;
    private final int protectFirstN;
    private final double tailTokenBudgetRatio;
    private final int ineffectiveThreshold;

    private CompressionConfig(Builder builder) {
        this.compressionThresholdRatio = builder.compressionThresholdRatio;
        this.protectFirstN = builder.protectFirstN;
        this.tailTokenBudgetRatio = builder.tailTokenBudgetRatio;
        this.ineffectiveThreshold = builder.ineffectiveThreshold;
    }

    public static Builder builder() { return new Builder(); }

    public double getCompressionThresholdRatio() { return compressionThresholdRatio; }
    public int getProtectFirstN() { return protectFirstN; }
    public double getTailTokenBudgetRatio() { return tailTokenBudgetRatio; }
    public int getIneffectiveThreshold() { return ineffectiveThreshold; }

    public static class Builder {
        private double compressionThresholdRatio = 0.5;
        private int protectFirstN = 3;
        private double tailTokenBudgetRatio = 0.4;
        private int ineffectiveThreshold = 2;

        public Builder compressionThresholdRatio(double v) { this.compressionThresholdRatio = v; return this; }
        public Builder protectFirstN(int v) { this.protectFirstN = v; return this; }
        public Builder tailTokenBudgetRatio(double v) { this.tailTokenBudgetRatio = v; return this; }
        public Builder ineffectiveThreshold(int v) { this.ineffectiveThreshold = v; return this; }

        public CompressionConfig build() { return new CompressionConfig(this); }
    }
}
