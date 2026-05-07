package com.ke.utopia.agent.memory.jdbc.autoconfigure;

import com.ke.utopia.agent.memory.jdbc.TkMapperMemoryStorage;
import com.ke.utopia.agent.memory.jdbc.mapper.*;
import com.ke.utopia.agent.memory.spi.MemoryStorage;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import tk.mybatis.mapper.common.Mapper;
import tk.mybatis.spring.annotation.MapperScan;

@AutoConfiguration
@ConditionalOnClass(name = "tk.mybatis.mapper.common.Mapper")
@ConditionalOnProperty(prefix = "memory-agent.jdbc-storage", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(TkMapperStorageProperties.class)
@MapperScan(basePackages = "com.ke.utopia.agent.memory.jdbc.mapper", markerInterface = Mapper.class)
public class TkMapperStorageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MemoryStorage.class)
    public MemoryStorage tkMapperMemoryStorage(
            SessionMapper sessionMapper,
            ConversationMessageMapper messageMapper,
            MemoryEntryMapper memoryEntryMapper,
            IntentSummaryMapper summaryMapper) {
        return new TkMapperMemoryStorage(sessionMapper, messageMapper, memoryEntryMapper, summaryMapper);
    }
}
