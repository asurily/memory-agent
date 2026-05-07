package com.ke.utopia.agent.memory.model;

/**
 * 消息角色枚举。
 */
public enum MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL;

    /**
     * 返回小写的角色值，用于 API 调用。
     */
    public String value() {
        return name().toLowerCase();
    }
}
