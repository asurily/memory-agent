package com.ke.utopia.agent.memory.config;

import java.util.Objects;

/**
 * Memory Agent 主配置对象。
 *
 * <p>设计原则：所有能力默认启用，应用方只需提供基础设施（LLM apiKey、自定义 EmbeddingService 等），
 * 无需逐个开关功能。调优参数（阈值、维度等）保留供精细控制。</p>
 *
 * <p>功能自动启用规则：
 * <ul>
 *   <li>安全扫描、指标收集、增量意图、自动管道(ASYNC) — 始终启用</li>
 *   <li>语义搜索、混合检索、重要性自学习、记忆衰退 — 始终启用（使用零依赖默认实现）</li>
 *   <li>自动记忆提取、冲突检测 — 有 LLM 时自动启用（提供 apiKey 或注入 IntentSummarizer）</li>
 * </ul>
 */
public final class MemoryAgentConfig {

    private final String storageType;
    private final int memoryCharLimit;
    private final int userProfileCharLimit;
    private final int summarizationMessageThreshold;
    private final int summarizationTokenThreshold;
    private final boolean summarizeOnSessionClose;
    private final double compressionThresholdRatio;
    private final int compressionProtectFirstN;

    // LLM 配置
    private final String llmApiKey;
    private final String llmModel;
    private final String llmBaseUrl;
    private final int llmMaxTokens;
    private final double llmTemperature;
    private final int llmTimeoutSeconds;
    private final String llmProxyHost;
    private final int llmProxyPort;

    // 调优参数
    private final double memoryExtractionConfidenceThreshold;
    private final int embeddingDimension;
    private final String vectorStoreType;
    private final int decayHalfLifeDays;
    private final double decayThreshold;
    private final long decayScheduleIntervalMinutes;
    private final double importanceProtectionThreshold;
    private final ConflictDetectionMode conflictDetectionMode;
    private final int autoCompressionContextWindowTokens;
    private final String keyParamsSchema;

    private MemoryAgentConfig(Builder builder) {
        this.storageType = builder.storageType;
        this.memoryCharLimit = builder.memoryCharLimit;
        this.userProfileCharLimit = builder.userProfileCharLimit;
        this.summarizationMessageThreshold = builder.summarizationMessageThreshold;
        this.summarizationTokenThreshold = builder.summarizationTokenThreshold;
        this.summarizeOnSessionClose = builder.summarizeOnSessionClose;
        this.compressionThresholdRatio = builder.compressionThresholdRatio;
        this.compressionProtectFirstN = builder.compressionProtectFirstN;

        this.llmApiKey = builder.llmApiKey;
        this.llmModel = builder.llmModel;
        this.llmBaseUrl = builder.llmBaseUrl;
        this.llmMaxTokens = builder.llmMaxTokens;
        this.llmTemperature = builder.llmTemperature;
        this.llmTimeoutSeconds = builder.llmTimeoutSeconds;
        this.llmProxyHost = builder.llmProxyHost;
        this.llmProxyPort = builder.llmProxyPort;

        this.memoryExtractionConfidenceThreshold = builder.memoryExtractionConfidenceThreshold;
        this.embeddingDimension = builder.embeddingDimension;
        this.vectorStoreType = builder.vectorStoreType;
        this.decayHalfLifeDays = builder.decayHalfLifeDays;
        this.decayThreshold = builder.decayThreshold;
        this.decayScheduleIntervalMinutes = builder.decayScheduleIntervalMinutes;
        this.importanceProtectionThreshold = builder.importanceProtectionThreshold;
        this.conflictDetectionMode = builder.conflictDetectionMode;
        this.autoCompressionContextWindowTokens = builder.autoCompressionContextWindowTokens;
        this.keyParamsSchema = builder.keyParamsSchema;
    }

    public static Builder builder() { return new Builder(); }

    // --- Basic ---
    public String getStorageType() { return storageType; }
    public int getMemoryCharLimit() { return memoryCharLimit; }
    public int getUserProfileCharLimit() { return userProfileCharLimit; }
    public int getSummarizationMessageThreshold() { return summarizationMessageThreshold; }
    public int getSummarizationTokenThreshold() { return summarizationTokenThreshold; }
    public boolean isSummarizeOnSessionClose() { return summarizeOnSessionClose; }
    public double getCompressionThresholdRatio() { return compressionThresholdRatio; }
    public int getCompressionProtectFirstN() { return compressionProtectFirstN; }

    // --- LLM ---
    public String getLlmApiKey() { return llmApiKey; }
    public String getLlmModel() { return llmModel; }
    public String getLlmBaseUrl() { return llmBaseUrl; }
    public int getLlmMaxTokens() { return llmMaxTokens; }
    public double getLlmTemperature() { return llmTemperature; }
    public int getLlmTimeoutSeconds() { return llmTimeoutSeconds; }
    public String getLlmProxyHost() { return llmProxyHost; }
    public int getLlmProxyPort() { return llmProxyPort; }
    public boolean hasProxy() { return llmProxyHost != null && !llmProxyHost.isEmpty() && llmProxyPort > 0; }

    // --- Tuning ---
    public double getMemoryExtractionConfidenceThreshold() { return memoryExtractionConfidenceThreshold; }
    public int getEmbeddingDimension() { return embeddingDimension; }
    public String getVectorStoreType() { return vectorStoreType; }
    public int getDecayHalfLifeDays() { return decayHalfLifeDays; }
    public double getDecayThreshold() { return decayThreshold; }
    public long getDecayScheduleIntervalMinutes() { return decayScheduleIntervalMinutes; }
    public double getImportanceProtectionThreshold() { return importanceProtectionThreshold; }
    public ConflictDetectionMode getConflictDetectionMode() { return conflictDetectionMode; }
    public int getAutoCompressionContextWindowTokens() { return autoCompressionContextWindowTokens; }
    public String getKeyParamsSchema() { return keyParamsSchema; }

    /**
     * 冲突检测模式（SYNC/ASYNC 仅在有 LLM 时生效）。
     */
    public enum ConflictDetectionMode {
        SYNC,  // 同步模式：写入时等待 LLM 检测结果
        ASYNC  // 异步模式：先写入，后台检测冲突并修正
    }

    @Override
    public String toString() {
        return "MemoryAgentConfig{storageType='" + storageType + "', llmModel='" + llmModel + "'}";
    }

    public static class Builder {
        private String storageType = "in-memory";
        private int memoryCharLimit = 2200;
        private int userProfileCharLimit = 1375;
        private int summarizationMessageThreshold = 10;
        private int summarizationTokenThreshold = 4000;
        private boolean summarizeOnSessionClose = true;
        private double compressionThresholdRatio = 0.5;
        private int compressionProtectFirstN = 3;

        private String llmApiKey;
        private String llmModel = "gpt-4o-mini";
        private String llmBaseUrl = "https://api.openai.com/v1";
        private int llmMaxTokens = 2000;
        private double llmTemperature = 0.3;
        private int llmTimeoutSeconds = 30;
        private String llmProxyHost;
        private int llmProxyPort;

        private double memoryExtractionConfidenceThreshold = 0.7;
        private int embeddingDimension = 1536;
        private String vectorStoreType = "in-memory";
        private int decayHalfLifeDays = 30;
        private double decayThreshold = 0.1;
        private long decayScheduleIntervalMinutes = 60;
        private double importanceProtectionThreshold = 0.8;
        private ConflictDetectionMode conflictDetectionMode = ConflictDetectionMode.SYNC;
        private int autoCompressionContextWindowTokens = 8000;
        private String keyParamsSchema;

        public Builder storageType(String storageType) { this.storageType = storageType; return this; }
        public Builder memoryCharLimit(int memoryCharLimit) { this.memoryCharLimit = memoryCharLimit; return this; }
        public Builder userProfileCharLimit(int userProfileCharLimit) { this.userProfileCharLimit = userProfileCharLimit; return this; }
        public Builder summarizationMessageThreshold(int v) { this.summarizationMessageThreshold = v; return this; }
        public Builder summarizationTokenThreshold(int v) { this.summarizationTokenThreshold = v; return this; }
        public Builder summarizeOnSessionClose(boolean v) { this.summarizeOnSessionClose = v; return this; }
        public Builder compressionThresholdRatio(double v) { this.compressionThresholdRatio = v; return this; }
        public Builder compressionProtectFirstN(int v) { this.compressionProtectFirstN = v; return this; }
        public Builder llmApiKey(String llmApiKey) { this.llmApiKey = llmApiKey; return this; }
        public Builder llmModel(String llmModel) { this.llmModel = llmModel; return this; }
        public Builder llmBaseUrl(String llmBaseUrl) { this.llmBaseUrl = llmBaseUrl; return this; }
        public Builder llmMaxTokens(int llmMaxTokens) { this.llmMaxTokens = llmMaxTokens; return this; }
        public Builder llmTemperature(double llmTemperature) { this.llmTemperature = llmTemperature; return this; }
        public Builder llmTimeoutSeconds(int llmTimeoutSeconds) { this.llmTimeoutSeconds = llmTimeoutSeconds; return this; }
        public Builder llmProxyHost(String llmProxyHost) { this.llmProxyHost = llmProxyHost; return this; }
        public Builder llmProxyPort(int llmProxyPort) { this.llmProxyPort = llmProxyPort; return this; }

        // Tuning parameters
        public Builder memoryExtractionConfidenceThreshold(double v) { this.memoryExtractionConfidenceThreshold = v; return this; }
        public Builder embeddingDimension(int v) { this.embeddingDimension = v; return this; }
        public Builder vectorStoreType(String v) { this.vectorStoreType = v; return this; }
        public Builder decayHalfLifeDays(int v) { this.decayHalfLifeDays = v; return this; }
        public Builder decayThreshold(double v) { this.decayThreshold = v; return this; }
        public Builder decayScheduleIntervalMinutes(long v) { this.decayScheduleIntervalMinutes = v; return this; }
        public Builder importanceProtectionThreshold(double v) { this.importanceProtectionThreshold = v; return this; }
        public Builder conflictDetectionMode(ConflictDetectionMode v) { this.conflictDetectionMode = v; return this; }
        public Builder autoCompressionContextWindowTokens(int v) { this.autoCompressionContextWindowTokens = v; return this; }
        public Builder keyParamsSchema(String v) { this.keyParamsSchema = v; return this; }

        public MemoryAgentConfig build() {
            Objects.requireNonNull(storageType, "storageType must not be null");
            return new MemoryAgentConfig(this);
        }
    }
}
