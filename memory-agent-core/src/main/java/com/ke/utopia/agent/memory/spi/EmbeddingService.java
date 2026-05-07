package com.ke.utopia.agent.memory.spi;

import java.util.List;

/**
 * 文本嵌入服务 SPI。
 * 默认实现：SimpleEmbeddingService（基于简单哈希，仅供开发测试）。
 * 生产环境应替换为真实 embedding 服务（如 Spring AI EmbeddingModel）。
 */
public interface EmbeddingService {

    /**
     * 将文本转换为向量嵌入。
     */
    float[] embed(String text);

    /**
     * 批量嵌入。
     */
    List<float[]> embedBatch(List<String> texts);

    /**
     * 返回嵌入维度。
     */
    int getDimension();
}
