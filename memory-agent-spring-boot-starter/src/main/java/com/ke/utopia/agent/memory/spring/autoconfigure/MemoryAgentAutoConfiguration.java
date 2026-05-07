package com.ke.utopia.agent.memory.spring.autoconfigure;

import com.ke.utopia.agent.memory.MemoryAgent;
import com.ke.utopia.agent.memory.config.MemoryAgentConfig;
import com.ke.utopia.agent.memory.spi.EmbeddingService;
import com.ke.utopia.agent.memory.spi.IntentSummarizer;
import com.ke.utopia.agent.memory.spi.KeywordSearchService;
import com.ke.utopia.agent.memory.spi.MemoryStorage;
import com.ke.utopia.agent.memory.spi.PromptStrategy;
import com.ke.utopia.agent.memory.spi.VectorStore;
import com.ke.utopia.agent.memory.spi.defaults.OpenAIIntentSummarizer;
import com.ke.utopia.agent.memory.spring.properties.MemoryAgentProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.function.Consumer;

@AutoConfiguration
@EnableConfigurationProperties(MemoryAgentProperties.class)
@ConditionalOnProperty(prefix = "memory-agent", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class MemoryAgentAutoConfiguration {


    @Bean
    @ConditionalOnMissingBean
    public MemoryAgentConfig memoryAgentConfig(MemoryAgentProperties props) {
        return MemoryAgentConfig.builder()
                .storageType(props.getStorageType())
                .memoryCharLimit(props.getMemoryCharLimit())
                .userProfileCharLimit(props.getUserProfileCharLimit())
                .summarizationMessageThreshold(props.getSummarizationMessageThreshold())
                .summarizationTokenThreshold(props.getSummarizationTokenThreshold())
                .summarizeOnSessionClose(props.isSummarizeOnSessionClose())
                .compressionThresholdRatio(props.getCompressionThresholdRatio())
                .llmApiKey(props.getLlm().getApiKey())
                .llmModel(props.getLlm().getModel())
                .llmBaseUrl(props.getLlm().getBaseUrl())
                .llmMaxTokens(props.getLlm().getMaxTokens())
                .llmTemperature(props.getLlm().getTemperature())
                .llmTimeoutSeconds(props.getLlm().getTimeoutSeconds())
                .llmProxyHost(props.getLlm().getProxyHost())
                .llmProxyPort(props.getLlm().getProxyPort())
                // Tuning parameters
                .memoryExtractionConfidenceThreshold(props.getMemoryExtractionConfidenceThreshold())
                .embeddingDimension(props.getEmbeddingDimension())
                .vectorStoreType(props.getVectorStoreType())
                .decayHalfLifeDays(props.getDecayHalfLifeDays())
                .decayThreshold(props.getDecayThreshold())
                .decayScheduleIntervalMinutes(props.getDecayScheduleIntervalMinutes())
                .importanceProtectionThreshold(props.getImportanceProtectionThreshold())
                .conflictDetectionMode(parseConflictMode(props.getConflictDetectionMode()))
                .autoCompressionContextWindowTokens(props.getAutoCompressionContextWindowTokens())
                .keyParamsSchema(props.getKeyParamsSchema())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(IntentSummarizer.class)
    public IntentSummarizer intentSummarizer(MemoryAgentConfig config) {
        return new OpenAIIntentSummarizer(config);
    }

    @Bean
    @ConditionalOnMissingBean(MemoryStorage.class)
    public MemoryStorage memoryStorage(MemoryAgentConfig config) {
        return null; // Let MemoryAgent handle it via SPI resolution
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public MemoryAgent memoryAgent(MemoryAgentConfig config,
                                    org.springframework.beans.factory.ObjectProvider<MemoryStorage> storageProvider,
                                    org.springframework.beans.factory.ObjectProvider<IntentSummarizer> summarizerProvider,
                                    org.springframework.beans.factory.ObjectProvider<EmbeddingService> embeddingProvider,
                                    org.springframework.beans.factory.ObjectProvider<VectorStore> vectorStoreProvider,
                                    org.springframework.beans.factory.ObjectProvider<KeywordSearchService> keywordProvider,
                                    org.springframework.beans.factory.ObjectProvider<PromptStrategy> promptStrategyProvider) {
        MemoryAgent.Builder builder = MemoryAgent.builder().config(config);
        applyIfPresent(storageProvider, builder::storage);
        applyIfPresent(summarizerProvider, builder::summarizer);
        applyIfPresent(embeddingProvider, builder::embeddingService);
        applyIfPresent(vectorStoreProvider, builder::vectorStore);
        applyIfPresent(keywordProvider, builder::keywordSearchService);
        applyIfPresent(promptStrategyProvider, builder::promptStrategy);
        return builder.build();
    }

    private static <T> void applyIfPresent(
            org.springframework.beans.factory.ObjectProvider<T> provider, Consumer<T> setter) {
        T bean = provider.getIfAvailable();
        if (bean != null) {
            setter.accept(bean);
        }
    }

    private static MemoryAgentConfig.ConflictDetectionMode parseConflictMode(String mode) {
        if (mode == null) return MemoryAgentConfig.ConflictDetectionMode.SYNC;
        try {
            return MemoryAgentConfig.ConflictDetectionMode.valueOf(mode.toUpperCase());
        } catch (IllegalArgumentException e) {
            return MemoryAgentConfig.ConflictDetectionMode.SYNC;
        }
    }
}
