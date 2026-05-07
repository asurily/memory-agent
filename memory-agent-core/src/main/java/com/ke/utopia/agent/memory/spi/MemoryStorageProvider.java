package com.ke.utopia.agent.memory.spi;

import com.ke.utopia.agent.memory.config.MemoryAgentConfig;

/**
 * MemoryStorage 工厂接口，支持 java.util.ServiceLoader 自动发现。
 */
public interface MemoryStorageProvider {

    /**
     * 存储提供者名称，如 "in-memory", "mysql", "redis"。
     */
    String name();

    /**
     * 创建存储实例。
     */
    MemoryStorage create(MemoryAgentConfig config);

    /**
     * 优先级，数值越小优先级越高。默认实现使用 100。
     */
    default int priority() {
        return 100;
    }
}
