package com.ke.utopia.agent.memory.spi;

import com.ke.utopia.agent.memory.model.VectorSearchResult;

import java.util.List;
import java.util.Map;

/**
 * 向量存储 SPI。
 * 默认实现：InMemoryVectorStore（内存存储，开发测试用）。
 * 生产环境应替换为真实向量数据库（如 Milvus、Pinecone、Weaviate）。
 */
public interface VectorStore {

    /**
     * 插入或更新向量。
     * @return 向量 ID
     */
    String upsert(String id, float[] embedding, Map<String, String> metadata);

    /**
     * 删除向量。
     */
    void delete(String id);

    /**
     * 向量相似度搜索。
     * @param queryEmbedding 查询向量
     * @param topK 返回前 K 个结果
     * @param filter 元数据过滤条件
     */
    List<VectorSearchResult> search(float[] queryEmbedding, int topK, Map<String, String> filter);

    /**
     * 初始化向量存储。
     */
    void initialize();

    /**
     * 关闭向量存储。
     */
    void shutdown();
}
