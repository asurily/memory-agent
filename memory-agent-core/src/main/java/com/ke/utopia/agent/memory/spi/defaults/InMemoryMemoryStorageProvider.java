package com.ke.utopia.agent.memory.spi.defaults;

import com.ke.utopia.agent.memory.config.MemoryAgentConfig;
import com.ke.utopia.agent.memory.spi.MemoryStorage;
import com.ke.utopia.agent.memory.spi.MemoryStorageProvider;

public final class InMemoryMemoryStorageProvider implements MemoryStorageProvider {

    @Override
    public String name() {
        return "in-memory";
    }

    @Override
    public MemoryStorage create(MemoryAgentConfig config) {
        return new InMemoryMemoryStorage();
    }

    @Override
    public int priority() {
        return 100;
    }
}
