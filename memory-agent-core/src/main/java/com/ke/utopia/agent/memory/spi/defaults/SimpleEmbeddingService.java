package com.ke.utopia.agent.memory.spi.defaults;

import com.ke.utopia.agent.memory.spi.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 TF-IDF 风格的本地 embedding 服务（无外部依赖）。
 * 使用词频哈希将文本映射为固定维度向量。
 * 生产环境应替换为真实 embedding 服务（如 OpenAI text-embedding-ada-002）。
 */
public final class SimpleEmbeddingService implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(SimpleEmbeddingService.class);

    private final int dimension;
    private final Map<String, Double> idfCache = new ConcurrentHashMap<>();

    public SimpleEmbeddingService(int dimension) {
        this.dimension = dimension;
        log.info("SimpleEmbeddingService initialized with dimension {}", dimension);
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[dimension];
        if (text == null || text.isEmpty()) return vector;

        String normalized = text.toLowerCase().trim();
        String[] tokens = normalized.split("\\s+");

        for (String token : tokens) {
            if (token.isEmpty()) continue;
            int[] positions = hashToken(token, dimension);
            float weight = 1.0f / (1 + token.length());
            for (int pos : positions) {
                vector[pos] += weight;
            }
        }

        // Normalize
        float norm = 0;
        for (float v : vector) norm += v * v;
        if (norm > 0) {
            float invNorm = (float) (1.0 / Math.sqrt(norm));
            for (int i = 0; i < vector.length; i++) {
                vector[i] *= invNorm;
            }
        }

        return vector;
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> results = new ArrayList<>(texts.size());
        for (String text : texts) {
            results.add(embed(text));
        }
        return results;
    }

    @Override
    public int getDimension() {
        return dimension;
    }

    private int[] hashToken(String token, int dimension) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(token.getBytes(StandardCharsets.UTF_8));

            // Use first 8 bytes for 4 positions (2 bytes each)
            int[] positions = new int[4];
            for (int i = 0; i < 4; i++) {
                positions[i] = Math.abs(((hash[i * 2] & 0xFF) << 8 | (hash[i * 2 + 1] & 0xFF))) % dimension;
            }
            return positions;
        } catch (Exception e) {
            // Fallback: simple hash
            int[] positions = new int[4];
            int h = token.hashCode();
            for (int i = 0; i < 4; i++) {
                positions[i] = Math.abs(h + i * 31) % dimension;
            }
            return positions;
        }
    }
}
