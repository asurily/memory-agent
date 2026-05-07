package com.ke.utopia.agent.memory.spring.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Memory Agent Spring Boot 配置属性。
 *
 * <p>功能自动启用规则参见 {@link com.ke.utopia.agent.memory.config.MemoryAgentConfig}。
 * 此处仅保留调优参数。</p>
 */
@ConfigurationProperties(prefix = "memory-agent")
public class MemoryAgentProperties {

    private boolean enabled = true;
    private String storageType = "in-memory";
    private int memoryCharLimit = 2200;
    private int userProfileCharLimit = 1375;
    private int summarizationMessageThreshold = 10;
    private int summarizationTokenThreshold = 4000;
    private boolean summarizeOnSessionClose = true;
    private double compressionThresholdRatio = 0.5;
    private LlmConfig llm = new LlmConfig();

    // 调优参数
    private double memoryExtractionConfidenceThreshold = 0.7;
    private int embeddingDimension = 1536;
    private String vectorStoreType = "in-memory";
    private int decayHalfLifeDays = 30;
    private double decayThreshold = 0.1;
    private long decayScheduleIntervalMinutes = 60;
    private double importanceProtectionThreshold = 0.8;

    /** 冲突检测模式: SYNC 或 ASYNC */
    private String conflictDetectionMode = "SYNC";

    private int autoCompressionContextWindowTokens = 8000;

    /** 增量意图提取的 keyParams schema（自定义领域参数模板） */
    private String keyParamsSchema;

    // --- Basic Getters/Setters ---

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getStorageType() { return storageType; }
    public void setStorageType(String storageType) { this.storageType = storageType; }

    public int getMemoryCharLimit() { return memoryCharLimit; }
    public void setMemoryCharLimit(int memoryCharLimit) { this.memoryCharLimit = memoryCharLimit; }

    public int getUserProfileCharLimit() { return userProfileCharLimit; }
    public void setUserProfileCharLimit(int userProfileCharLimit) { this.userProfileCharLimit = userProfileCharLimit; }

    public int getSummarizationMessageThreshold() { return summarizationMessageThreshold; }
    public void setSummarizationMessageThreshold(int v) { this.summarizationMessageThreshold = v; }

    public int getSummarizationTokenThreshold() { return summarizationTokenThreshold; }
    public void setSummarizationTokenThreshold(int v) { this.summarizationTokenThreshold = v; }

    public boolean isSummarizeOnSessionClose() { return summarizeOnSessionClose; }
    public void setSummarizeOnSessionClose(boolean v) { this.summarizeOnSessionClose = v; }

    public double getCompressionThresholdRatio() { return compressionThresholdRatio; }
    public void setCompressionThresholdRatio(double v) { this.compressionThresholdRatio = v; }

    public LlmConfig getLlm() { return llm; }
    public void setLlm(LlmConfig llm) { this.llm = llm; }

    // --- Tuning Parameters ---

    public double getMemoryExtractionConfidenceThreshold() { return memoryExtractionConfidenceThreshold; }
    public void setMemoryExtractionConfidenceThreshold(double v) { this.memoryExtractionConfidenceThreshold = v; }

    public int getEmbeddingDimension() { return embeddingDimension; }
    public void setEmbeddingDimension(int v) { this.embeddingDimension = v; }

    public String getVectorStoreType() { return vectorStoreType; }
    public void setVectorStoreType(String v) { this.vectorStoreType = v; }

    public int getDecayHalfLifeDays() { return decayHalfLifeDays; }
    public void setDecayHalfLifeDays(int v) { this.decayHalfLifeDays = v; }

    public double getDecayThreshold() { return decayThreshold; }
    public void setDecayThreshold(double v) { this.decayThreshold = v; }

    public long getDecayScheduleIntervalMinutes() { return decayScheduleIntervalMinutes; }
    public void setDecayScheduleIntervalMinutes(long v) { this.decayScheduleIntervalMinutes = v; }

    public double getImportanceProtectionThreshold() { return importanceProtectionThreshold; }
    public void setImportanceProtectionThreshold(double v) { this.importanceProtectionThreshold = v; }

    public String getConflictDetectionMode() { return conflictDetectionMode; }
    public void setConflictDetectionMode(String v) { this.conflictDetectionMode = v; }

    public int getAutoCompressionContextWindowTokens() { return autoCompressionContextWindowTokens; }
    public void setAutoCompressionContextWindowTokens(int v) { this.autoCompressionContextWindowTokens = v; }

    public String getKeyParamsSchema() { return keyParamsSchema; }
    public void setKeyParamsSchema(String v) { this.keyParamsSchema = v; }

    public static class LlmConfig {
        private String apiKey;
        private String model = "gpt-4o-mini";
        private String baseUrl = "https://api.openai.com/v1";
        private int maxTokens = 2000;
        private double temperature = 0.3;
        private int timeoutSeconds = 30;
        private String proxyHost;
        private int proxyPort;

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }

        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }

        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

        public String getProxyHost() { return proxyHost; }
        public void setProxyHost(String proxyHost) { this.proxyHost = proxyHost; }

        public int getProxyPort() { return proxyPort; }
        public void setProxyPort(int proxyPort) { this.proxyPort = proxyPort; }
    }
}
