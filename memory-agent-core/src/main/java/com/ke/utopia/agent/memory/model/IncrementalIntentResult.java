package com.ke.utopia.agent.memory.model;

import java.util.Map;

/**
 * 增量意图提取结果。
 * <p>
 * 由 {@link com.ke.utopia.agent.memory.spi.IntentSummarizer#extractIncrementalIntent}
 * 返回，包含 LLM 提取的增量意图信息。
 */
public final class IncrementalIntentResult {

    private final String coreIntent;
    private final Map<String, String> keyParams;
    private final boolean hasChanged;
    private final String reasoning;
    private final double confidence;

    public IncrementalIntentResult(String coreIntent,
                                    Map<String, String> keyParams,
                                    boolean hasChanged,
                                    String reasoning,
                                    double confidence) {
        this.coreIntent = coreIntent;
        this.keyParams = keyParams;
        this.hasChanged = hasChanged;
        this.reasoning = reasoning;
        this.confidence = confidence;
    }

    public String getCoreIntent() {
        return coreIntent;
    }

    public Map<String, String> getKeyParams() {
        return keyParams;
    }

    public boolean hasChanged() {
        return hasChanged;
    }

    public String getReasoning() {
        return reasoning;
    }

    public double getConfidence() {
        return confidence;
    }

    @Override
    public String toString() {
        return "IncrementalIntentResult{" +
                "coreIntent='" + coreIntent + '\'' +
                ", keyParams=" + keyParams +
                ", hasChanged=" + hasChanged +
                ", reasoning='" + reasoning + '\'' +
                ", confidence=" + confidence +
                '}';
    }
}
