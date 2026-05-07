package com.ke.utopia.agent.memory.model;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 跨会话用户画像。
 */
public final class UserProfile {

    private final String userId;
    private final List<MemoryEntry> profileEntries;
    private final List<MemoryEntry> memoryEntries;
    private final Instant lastUpdatedAt;
    private final Map<String, String> metadata;

    public UserProfile(String userId, List<MemoryEntry> profileEntries,
                       List<MemoryEntry> memoryEntries, Instant lastUpdatedAt,
                       Map<String, String> metadata) {
        this.userId = userId;
        this.profileEntries = Collections.unmodifiableList(profileEntries);
        this.memoryEntries = Collections.unmodifiableList(memoryEntries);
        this.lastUpdatedAt = lastUpdatedAt;
        this.metadata = Collections.unmodifiableMap(metadata);
    }

    public static UserProfile empty(String userId) {
        return new UserProfile(userId, Collections.emptyList(), Collections.emptyList(),
                Instant.now(), Collections.emptyMap());
    }

    public int totalCharCount() {
        return profileEntries.stream().mapToInt(e -> e.getContent().length()).sum() +
                memoryEntries.stream().mapToInt(e -> e.getContent().length()).sum();
    }

    public String getUserId() { return userId; }
    public List<MemoryEntry> getProfileEntries() { return profileEntries; }
    public List<MemoryEntry> getMemoryEntries() { return memoryEntries; }
    public Instant getLastUpdatedAt() { return lastUpdatedAt; }
    public Map<String, String> getMetadata() { return metadata; }

    @Override
    public String toString() {
        return "UserProfile{userId='" + userId + "', profileEntries=" + profileEntries.size() +
                ", memoryEntries=" + memoryEntries.size() + ", totalChars=" + totalCharCount() + "}";
    }
}
