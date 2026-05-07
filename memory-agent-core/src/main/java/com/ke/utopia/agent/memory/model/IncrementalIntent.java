package com.ke.utopia.agent.memory.model;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 增量意图。
 * <p>
 * 轻量级意图表示，在每轮对话中基于规则推导或 LLM 增强生成。
 * 与 {@link IntentSummary} 不同，IncrementalIntent 不需要累积大量消息即可触发，
 * 适用于早期轮次（第 1-9 轮）的实时意图推导。
 */
public final class IncrementalIntent {

    private final String id;
    private final String coreIntent;
    private final Map<String, String> keyParams;
    private final IntentSource source;
    private final double confidence;
    private final Instant updatedAt;
    private final String reasoning;

    private IncrementalIntent(Builder builder) {
        this.id = builder.id;
        this.coreIntent = builder.coreIntent;
        this.keyParams = Collections.unmodifiableMap(new HashMap<>(builder.keyParams));
        this.source = builder.source;
        this.confidence = builder.confidence;
        this.updatedAt = builder.updatedAt;
        this.reasoning = builder.reasoning;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 创建一个空的增量意图（用于初始化）。
     */
    public static IncrementalIntent empty() {
        return builder()
                .coreIntent("")
                .source(IntentSource.RULE_BASED)
                .confidence(0.0)
                .build();
    }

    public String getId() {
        return id;
    }

    /**
     * 核心意图描述。
     * 例如："构建订单管理 API，使用 java+mysql"
     */
    public String getCoreIntent() {
        return coreIntent;
    }

    /**
     * 关键参数映射。
     * 例如：{language: "java", database: "mysql", module: "order"}
     */
    public Map<String, String> getKeyParams() {
        return keyParams;
    }

    /**
     * 意图来源。
     */
    public IntentSource getSource() {
        return source;
    }

    /**
     * 置信度（0.0 - 1.0）。
     */
    public double getConfidence() {
        return confidence;
    }

    /**
     * 更新时间。
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * 推导理由。
     * 例如："检测到数据库从 redis 变更为 mysql"
     */
    public String getReasoning() {
        return reasoning;
    }

    /**
     * 获取指定参数的值。
     */
    public String getParam(String key) {
        return keyParams.get(key);
    }

    /**
     * 判断是否为空意图。
     */
    public boolean isEmpty() {
        return coreIntent == null || coreIntent.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IncrementalIntent)) return false;
        IncrementalIntent that = (IncrementalIntent) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "IncrementalIntent{" +
                "id='" + id + '\'' +
                ", coreIntent='" + coreIntent + '\'' +
                ", keyParams=" + keyParams +
                ", source=" + source +
                ", confidence=" + confidence +
                ", reasoning='" + reasoning + '\'' +
                '}';
    }

    /**
     * 创建一个新的 Builder，复制当前实例的值。
     */
    public Builder toBuilder() {
        return new Builder()
                .id(id)
                .coreIntent(coreIntent)
                .keyParams(keyParams)
                .source(source)
                .confidence(confidence)
                .updatedAt(updatedAt)
                .reasoning(reasoning);
    }

    /**
     * 意图来源枚举。
     */
    public enum IntentSource {
        /**
         * 基于规则推导。
         */
        RULE_BASED,

        /**
         * LLM 提取。
         */
        LLM_EXTRACTED,

        /**
         * 从 IntentSummary 派生。
         */
        SUMMARY_DERIVED,

        /**
         * 从记忆快照推断。
         */
        MEMORY_INFERRED
    }

    public static class Builder {
        private String id = UUID.randomUUID().toString();
        private String coreIntent = "";
        private Map<String, String> keyParams = new HashMap<>();
        private IntentSource source = IntentSource.RULE_BASED;
        private double confidence = 0.5;
        private Instant updatedAt = Instant.now();
        private String reasoning;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder coreIntent(String coreIntent) {
            this.coreIntent = coreIntent != null ? coreIntent : "";
            return this;
        }

        public Builder keyParams(Map<String, String> keyParams) {
            this.keyParams = keyParams != null ? new HashMap<>(keyParams) : new HashMap<>();
            return this;
        }

        public Builder addParam(String key, String value) {
            if (key != null && value != null) {
                this.keyParams.put(key, value);
            }
            return this;
        }

        public Builder removeParam(String key) {
            this.keyParams.remove(key);
            return this;
        }

        public Builder source(IntentSource source) {
            this.source = source;
            return this;
        }

        public Builder confidence(double confidence) {
            this.confidence = Math.max(0.0, Math.min(1.0, confidence));
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Builder reasoning(String reasoning) {
            this.reasoning = reasoning;
            return this;
        }

        public IncrementalIntent build() {
            return new IncrementalIntent(this);
        }
    }
}
