package com.ke.utopia.agent.memory.spi;

import com.ke.utopia.agent.memory.model.VectorSearchResult;

import java.util.List;

/**
 * 关键词搜索服务 SPI。
 * 提供基于 BM25 等关键词算法的记忆检索能力，与向量搜索互补。
 */
public interface KeywordSearchService {

    /**
     * 关键词搜索。
     *
     * @param query  搜索查询
     * @param topK   返回 topK 条结果
     * @param userId 用户 ID（用于过滤）
     * @return 搜索结果列表
     */
    List<VectorSearchResult> search(String query, int topK, String userId);

    /**
     * 索引一条文档。
     *
     * @param id      文档 ID（记忆条目 ID）
     * @param content 文档内容
     * @param userId  用户 ID
     */
    void index(String id, String content, String userId);

    /**
     * 移除一条文档索引。
     *
     * @param id 文档 ID
     */
    void remove(String id);

    /**
     * 初始化服务。
     */
    default void initialize() {}

    /**
     * 关闭服务，释放资源。
     */
    default void shutdown() {}
}
