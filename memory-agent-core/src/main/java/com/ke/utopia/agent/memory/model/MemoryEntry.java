package com.ke.utopia.agent.memory.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 单条记忆条目（不可变值对象）。
 * 类比 Hermes MEMORY.md 中以 § 分隔的每一段内容。
 */
public final class MemoryEntry {

    private final String id;
    private final String content;
    private final MemoryType type;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant lastAccessedAt;
    private final int accessCount;
    private final double importanceScore;
    private final MemoryTier tier;

    private MemoryEntry(String id, String content, MemoryType type,
                        Instant createdAt, Instant updatedAt,
                        Instant lastAccessedAt, int accessCount,
                        double importanceScore, MemoryTier tier) {
        this.id = id;
        this.content = content;
        this.type = type;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.lastAccessedAt = lastAccessedAt;
        this.accessCount = accessCount;
        this.importanceScore = importanceScore;
        this.tier = tier;
    }

    public static MemoryEntry reconstruct(String id, String content, MemoryType type,
                                           Instant createdAt, Instant updatedAt,
                                           Instant lastAccessedAt, int accessCount,
                                           double importanceScore, MemoryTier tier) {
        return new MemoryEntry(id, content, type, createdAt, updatedAt,
                lastAccessedAt, accessCount, importanceScore, tier);
    }

    public static MemoryEntry of(String content, MemoryType type) {
        Instant now = Instant.now();
        return new MemoryEntry(UUID.randomUUID().toString(), content, type, now, now, now, 0, 0.5, MemoryTier.CORE);
    }

    public MemoryEntry withContent(String newContent) {
        return new MemoryEntry(this.id, newContent, this.type, this.createdAt, Instant.now(),
                this.lastAccessedAt, this.accessCount, this.importanceScore, this.tier);
    }

    public MemoryEntry withAccessUpdate() {
        return new MemoryEntry(this.id, this.content, this.type, this.createdAt, this.updatedAt,
                Instant.now(), this.accessCount + 1, this.importanceScore, this.tier);
    }

    public MemoryEntry withImportanceScore(double score) {
        return new MemoryEntry(this.id, this.content, this.type, this.createdAt, this.updatedAt,
                this.lastAccessedAt, this.accessCount, score, this.tier);
    }

    public MemoryEntry withTier(MemoryTier newTier) {
        return new MemoryEntry(this.id, this.content, this.type, this.createdAt, this.updatedAt,
                this.lastAccessedAt, this.accessCount, this.importanceScore, newTier);
    }

    public String getId() { return id; }
    public String getContent() { return content; }
    public MemoryType getType() { return type; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getLastAccessedAt() { return lastAccessedAt; }
    public int getAccessCount() { return accessCount; }
    public double getImportanceScore() { return importanceScore; }
    public MemoryTier getTier() { return tier; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MemoryEntry)) return false;
        MemoryEntry that = (MemoryEntry) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "MemoryEntry{id='" + id + "', type=" + type + ", tier=" + tier +
                ", importance=" + importanceScore + ", content='" +
                (content.length() > 50 ? content.substring(0, 50) + "..." : content) + "'}";
    }
}
