package com.ke.utopia.agent.memory.jdbc.spi;

import com.ke.utopia.agent.memory.config.MemoryAgentConfig;
import com.ke.utopia.agent.memory.spi.MemoryStorage;
import com.ke.utopia.agent.memory.spi.MemoryStorageProvider;

/**
 * SPI 工厂（Spring Boot 环境下由自动配置注入，非 Boot 环境需要手动组装）。
 */
public class TkMapperStorageProvider implements MemoryStorageProvider {

    @Override
    public String name() {
        return "mysql-tkmapper";
    }

    @Override
    public MemoryStorage create(MemoryAgentConfig config) {
        throw new UnsupportedOperationException(
                "TkMapper storage requires Spring Boot. "
                + "Use auto-configuration or construct TkMapperMemoryStorage manually.");
    }

    @Override
    public int priority() {
        return 50;
    }
}
