package com.ke.utopia.agent.memory.spi;

import java.util.Objects;

/**
 * Prompt 模板值对象，由 {@link PromptStrategy} 的各个方法返回。
 *
 * <p>包含可选的 systemMessage 和必选的 userMessage。
 * 当 systemMessage 为 null 时，LLM 调用方使用单条 user message（向后兼容）。</p>
 */
public final class PromptTemplate {

    private final String systemMessage;
    private final String userMessage;

    private PromptTemplate(String systemMessage, String userMessage) {
        this.systemMessage = systemMessage;
        this.userMessage = Objects.requireNonNull(userMessage, "userMessage must not be null");
    }

    /**
     * 创建仅包含 user message 的模板（无 system message，向后兼容）。
     */
    public static PromptTemplate of(String userMessage) {
        return new PromptTemplate(null, userMessage);
    }

    /**
     * 创建包含 system message 和 user message 的模板。
     */
    public static PromptTemplate of(String systemMessage, String userMessage) {
        return new PromptTemplate(systemMessage, userMessage);
    }

    /**
     * 获取 system message，可能为 null。
     */
    public String getSystemMessage() {
        return systemMessage;
    }

    /**
     * 获取 user message，永不为 null。
     */
    public String getUserMessage() {
        return userMessage;
    }

    /**
     * 是否包含 system message。
     */
    public boolean hasSystemMessage() {
        return systemMessage != null;
    }
}
