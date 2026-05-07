package com.ke.utopia.agent.memory.summary;

/**
 * 意图总结触发配置。
 */
public final class SummarizationConfig {

    private final int messageCountThreshold;
    private final int tokenCountThreshold;
    private final boolean summarizeOnClose;
    private final boolean summarizeOnExplicit;
    private final int maxRetries;

    private SummarizationConfig(Builder builder) {
        this.messageCountThreshold = builder.messageCountThreshold;
        this.tokenCountThreshold = builder.tokenCountThreshold;
        this.summarizeOnClose = builder.summarizeOnClose;
        this.summarizeOnExplicit = builder.summarizeOnExplicit;
        this.maxRetries = builder.maxRetries;
    }

    public static Builder builder() { return new Builder(); }

    public int getMessageCountThreshold() { return messageCountThreshold; }
    public int getTokenCountThreshold() { return tokenCountThreshold; }
    public boolean isSummarizeOnClose() { return summarizeOnClose; }
    public boolean isSummarizeOnExplicit() { return summarizeOnExplicit; }
    public int getMaxRetries() { return maxRetries; }

    public static class Builder {
        private int messageCountThreshold = 10;
        private int tokenCountThreshold = 4000;
        private boolean summarizeOnClose = true;
        private boolean summarizeOnExplicit = true;
        private int maxRetries = 3;

        public Builder messageCountThreshold(int v) { this.messageCountThreshold = v; return this; }
        public Builder tokenCountThreshold(int v) { this.tokenCountThreshold = v; return this; }
        public Builder summarizeOnClose(boolean v) { this.summarizeOnClose = v; return this; }
        public Builder summarizeOnExplicit(boolean v) { this.summarizeOnExplicit = v; return this; }
        public Builder maxRetries(int v) { this.maxRetries = v; return this; }

        public SummarizationConfig build() {
            return new SummarizationConfig(this);
        }
    }
}
