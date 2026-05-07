package com.ke.utopia.agent.memory.spi.defaults;

import com.ke.utopia.agent.memory.exception.SessionNotFoundException;
import com.ke.utopia.agent.memory.model.*;
import com.ke.utopia.agent.memory.model.MemoryTier;
import com.ke.utopia.agent.memory.spi.MemoryStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 基于 ConcurrentHashMap 的线程安全内存存储。
 * 使用 compute() 保证原子 read-modify-write。
 */
public final class InMemoryMemoryStorage implements MemoryStorage {

    private static final Logger log = LoggerFactory.getLogger(InMemoryMemoryStorage.class);

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, List<ConversationMessage>> sessionMessages = new ConcurrentHashMap<>();
    private final Map<String, List<MemoryEntry>> userMemoryEntries = new ConcurrentHashMap<>();
    private final Map<String, List<IntentSummary>> sessionSummaries = new ConcurrentHashMap<>();

    @Override
    public void initialize() {
        log.info("InMemoryMemoryStorage initialized");
    }

    @Override
    public void shutdown() {
        sessions.clear();
        sessionMessages.clear();
        userMemoryEntries.clear();
        sessionSummaries.clear();
        log.info("InMemoryMemoryStorage shut down");
    }

    // --- Session ---

    @Override
    public Session createSession(String userId, String source) {
        Session session = Session.create(userId, source);
        sessions.put(session.getId(), session);
        sessionMessages.put(session.getId(), new ArrayList<>());
        return session;
    }

    @Override
    public Optional<Session> getSession(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    @Override
    public List<Session> getSessionsByUser(String userId) {
        return sessions.values().stream()
                .filter(s -> s.getUserId().equals(userId))
                .collect(Collectors.toList());
    }

    @Override
    public Session updateSession(Session session) {
        if (!sessions.containsKey(session.getId())) {
            throw new SessionNotFoundException(session.getId());
        }
        sessions.put(session.getId(), session);
        return session;
    }

    @Override
    public void closeSession(String sessionId) {
        Session session = sessions.get(sessionId);
        if (session != null) {
            sessions.put(sessionId, session.close());
        }
    }

    // --- Message ---

    @Override
    public ConversationMessage addMessage(ConversationMessage message) {
        sessionMessages.computeIfAbsent(message.getSessionId(), k -> new ArrayList<>()).add(message);
        return message;
    }

    @Override
    public List<ConversationMessage> getMessages(String sessionId) {
        return Collections.unmodifiableList(
                sessionMessages.getOrDefault(sessionId, Collections.emptyList()));
    }

    @Override
    public List<ConversationMessage> getRecentMessages(String sessionId, int limit) {
        List<ConversationMessage> all = sessionMessages.getOrDefault(sessionId, Collections.emptyList());
        if (all.size() <= limit) {
            return Collections.unmodifiableList(all);
        }
        return Collections.unmodifiableList(all.subList(all.size() - limit, all.size()));
    }

    @Override
    public int getMessageCount(String sessionId) {
        return sessionMessages.getOrDefault(sessionId, Collections.emptyList()).size();
    }

    // --- Memory Entry ---

    @Override
    public MemoryEntry addMemoryEntry(String userId, MemoryEntry entry) {
        userMemoryEntries.compute(userId, (k, entries) -> {
            List<MemoryEntry> list = entries != null ? new ArrayList<>(entries) : new ArrayList<>();
            // Dedup
            if (list.stream().anyMatch(e -> e.getContent().equals(entry.getContent()))) {
                return entries;
            }
            list.add(entry);
            return list;
        });
        return entry;
    }

    @Override
    public Optional<MemoryEntry> replaceMemoryEntry(String userId, String entryId, String newContent) {
        AtomicReference<MemoryEntry> result = new AtomicReference<>();
        userMemoryEntries.computeIfPresent(userId, (k, entries) -> {
            List<MemoryEntry> list = new ArrayList<>(entries);
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).getId().equals(entryId)) {
                    MemoryEntry updated = list.get(i).withContent(newContent);
                    list.set(i, updated);
                    result.set(updated);
                    break;
                }
            }
            return list;
        });
        return Optional.ofNullable(result.get());
    }

    @Override
    public boolean removeMemoryEntry(String userId, String entryId) {
        boolean[] removed = {false};
        userMemoryEntries.computeIfPresent(userId, (k, entries) -> {
            List<MemoryEntry> list = new ArrayList<>(entries);
            removed[0] = list.removeIf(e -> e.getId().equals(entryId));
            return list;
        });
        return removed[0];
    }

    @Override
    public List<MemoryEntry> getMemoryEntries(String userId, MemoryType type) {
        List<MemoryEntry> entries = userMemoryEntries.getOrDefault(userId, Collections.emptyList());
        return entries.stream()
                .filter(e -> e.getType() == type)
                .collect(Collectors.toList());
    }

    @Override
    public List<MemoryEntry> getMemoryEntriesByTier(String userId, MemoryTier tier) {
        List<MemoryEntry> entries = userMemoryEntries.getOrDefault(userId, Collections.emptyList());
        return entries.stream()
                .filter(e -> e.getTier() == tier)
                .collect(Collectors.toList());
    }

    @Override
    public boolean updateMemoryTier(String userId, String entryId, MemoryTier newTier) {
        boolean[] updated = {false};
        userMemoryEntries.computeIfPresent(userId, (k, entries) -> {
            List<MemoryEntry> list = new ArrayList<>(entries);
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).getId().equals(entryId)) {
                    list.set(i, list.get(i).withTier(newTier));
                    updated[0] = true;
                    break;
                }
            }
            return list;
        });
        return updated[0];
    }

    @Override
    public int getMemoryEntryCountByTier(String userId, MemoryTier tier) {
        return (int) userMemoryEntries.getOrDefault(userId, Collections.emptyList()).stream()
                .filter(e -> e.getTier() == tier)
                .count();
    }

    @Override
    public Optional<MemoryEntry> getMemoryEntry(String userId, String entryId) {
        return userMemoryEntries.getOrDefault(userId, Collections.emptyList()).stream()
                .filter(e -> e.getId().equals(entryId))
                .findFirst();
    }

    @Override
    public boolean updateMemoryEntry(String userId, MemoryEntry updatedEntry) {
        boolean[] updated = {false};
        userMemoryEntries.computeIfPresent(userId, (k, entries) -> {
            List<MemoryEntry> list = new ArrayList<>(entries);
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).getId().equals(updatedEntry.getId())) {
                    list.set(i, updatedEntry);
                    updated[0] = true;
                    break;
                }
            }
            return list;
        });
        return updated[0];
    }

    @Override
    public List<String> getAllUserIds() {
        return new ArrayList<>(userMemoryEntries.keySet());
    }

    // --- User Profile ---

    @Override
    public UserProfile getUserProfile(String userId) {
        List<MemoryEntry> profileEntries = getMemoryEntries(userId, MemoryType.USER_PROFILE);
        List<MemoryEntry> memoryEntries = getMemoryEntries(userId, MemoryType.MEMORY);
        Instant lastUpdated = memoryEntries.stream()
                .map(MemoryEntry::getUpdatedAt)
                .max(Comparator.naturalOrder())
                .orElse(Instant.now());
        return new UserProfile(userId, profileEntries, memoryEntries, lastUpdated, Collections.emptyMap());
    }

    // --- Intent Summary ---

    @Override
    public IntentSummary saveIntentSummary(IntentSummary summary) {
        sessionSummaries.computeIfAbsent(summary.getSessionId(), k -> new ArrayList<>()).add(summary);
        return summary;
    }

    @Override
    public List<IntentSummary> getIntentSummaries(String sessionId) {
        return Collections.unmodifiableList(
                sessionSummaries.getOrDefault(sessionId, Collections.emptyList()));
    }

    @Override
    public List<IntentSummary> getIntentSummariesByUser(String userId) {
        return sessions.values().stream()
                .filter(s -> s.getUserId().equals(userId))
                .map(Session::getId)
                .flatMap(sid -> sessionSummaries.getOrDefault(sid, Collections.emptyList()).stream())
                .collect(Collectors.toList());
    }
}
