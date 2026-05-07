package com.ke.utopia.agent.memory.jdbc;

import com.ke.utopia.agent.memory.jdbc.mapper.*;
import com.ke.utopia.agent.memory.model.*;
import com.ke.utopia.agent.memory.spi.MemoryStorage;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TkMapperMemoryStorageTest {

    @Autowired
    private MemoryStorage storage;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.execute("DELETE FROM ma_intent_summary");
        jdbcTemplate.execute("DELETE FROM ma_conversation_message");
        jdbcTemplate.execute("DELETE FROM ma_memory_entry");
        jdbcTemplate.execute("DELETE FROM ma_session");
    }

    // --- Session ---

    @Test
    @Order(1)
    void createSession_shouldPersist() {
        Session session = storage.createSession("user1", "test");
        assertNotNull(session.getId());
        assertEquals("user1", session.getUserId());
        assertEquals("test", session.getSource());
        assertEquals(SessionStatus.ACTIVE, session.getStatus());
    }

    @Test
    @Order(2)
    void getSession_shouldReturnPersisted() {
        Session created = storage.createSession("user1", "test");
        Optional<Session> found = storage.getSession(created.getId());
        assertTrue(found.isPresent());
        assertEquals(created.getId(), found.get().getId());
    }

    @Test
    @Order(3)
    void getSessionsByUser_shouldReturnAll() {
        storage.createSession("user1", "test1");
        storage.createSession("user1", "test2");
        storage.createSession("user2", "test3");
        List<Session> sessions = storage.getSessionsByUser("user1");
        assertEquals(2, sessions.size());
    }

    @Test
    @Order(4)
    void updateSession_shouldPersistChanges() {
        Session created = storage.createSession("user1", "test");
        Session updated = new Session.Builder().from(created).title("New Title").build();
        storage.updateSession(updated);
        Optional<Session> found = storage.getSession(created.getId());
        assertTrue(found.isPresent());
        assertEquals("New Title", found.get().getTitle());
    }

    @Test
    @Order(5)
    void closeSession_shouldSetStatus() {
        Session created = storage.createSession("user1", "test");
        storage.closeSession(created.getId());
        Optional<Session> found = storage.getSession(created.getId());
        assertTrue(found.isPresent());
        assertEquals(SessionStatus.CLOSED, found.get().getStatus());
        assertNotNull(found.get().getEndedAt());
    }

    // --- Message ---

    @Test
    @Order(6)
    void addMessage_shouldPersist() {
        Session session = storage.createSession("user1", "test");
        ConversationMessage msg = ConversationMessage.userMessage(session.getId(), "Hello");
        storage.addMessage(msg);
        List<ConversationMessage> messages = storage.getMessages(session.getId());
        assertEquals(1, messages.size());
        assertEquals("Hello", messages.get(0).getContent());
    }

    @Test
    @Order(7)
    void getRecentMessages_shouldReturnInOrder() {
        Session session = storage.createSession("user1", "test");
        for (int i = 0; i < 5; i++) {
            storage.addMessage(ConversationMessage.userMessage(session.getId(), "msg" + i));
        }
        List<ConversationMessage> recent = storage.getRecentMessages(session.getId(), 3);
        assertEquals(3, recent.size());
        assertEquals("msg2", recent.get(0).getContent());
        assertEquals("msg4", recent.get(2).getContent());
    }

    @Test
    @Order(8)
    void getMessageCount_shouldReturnCorrect() {
        Session session = storage.createSession("user1", "test");
        storage.addMessage(ConversationMessage.userMessage(session.getId(), "msg1"));
        storage.addMessage(ConversationMessage.assistantMessage(session.getId(), "msg2"));
        assertEquals(2, storage.getMessageCount(session.getId()));
    }

    // --- Memory Entry ---

    @Test
    @Order(9)
    void addMemoryEntry_shouldPersist() {
        MemoryEntry entry = MemoryEntry.of("test memory", MemoryType.MEMORY);
        storage.addMemoryEntry("user1", entry);
        List<MemoryEntry> entries = storage.getMemoryEntries("user1", MemoryType.MEMORY);
        assertEquals(1, entries.size());
        assertEquals("test memory", entries.get(0).getContent());
    }

    @Test
    @Order(10)
    void addMemoryEntry_shouldDedup() {
        MemoryEntry e1 = MemoryEntry.of("duplicate content", MemoryType.MEMORY);
        storage.addMemoryEntry("user1", e1);
        MemoryEntry e2 = MemoryEntry.of("duplicate content", MemoryType.MEMORY);
        storage.addMemoryEntry("user1", e2);
        List<MemoryEntry> entries = storage.getMemoryEntries("user1", MemoryType.MEMORY);
        assertEquals(1, entries.size());
    }

    @Test
    @Order(11)
    void replaceMemoryEntry_shouldUpdateContent() {
        MemoryEntry entry = MemoryEntry.of("old content", MemoryType.MEMORY);
        storage.addMemoryEntry("user1", entry);
        Optional<MemoryEntry> replaced = storage.replaceMemoryEntry("user1", entry.getId(), "new content");
        assertTrue(replaced.isPresent());
        assertEquals("new content", replaced.get().getContent());
    }

    @Test
    @Order(12)
    void removeMemoryEntry_shouldDelete() {
        MemoryEntry entry = MemoryEntry.of("to remove", MemoryType.MEMORY);
        storage.addMemoryEntry("user1", entry);
        assertTrue(storage.removeMemoryEntry("user1", entry.getId()));
        List<MemoryEntry> entries = storage.getMemoryEntries("user1", MemoryType.MEMORY);
        assertTrue(entries.isEmpty());
    }

    // --- Tiered Memory ---

    @Test
    @Order(13)
    void getMemoryEntriesByTier_shouldFilterCorrectly() {
        MemoryEntry e1 = MemoryEntry.of("core memory", MemoryType.MEMORY);
        storage.addMemoryEntry("user1", e1);
        storage.updateMemoryTier("user1", e1.getId(), MemoryTier.ARCHIVED);
        MemoryEntry e2 = MemoryEntry.of("another core", MemoryType.MEMORY);
        storage.addMemoryEntry("user1", e2);
        assertEquals(1, storage.getMemoryEntriesByTier("user1", MemoryTier.ARCHIVED).size());
        assertEquals(1, storage.getMemoryEntriesByTier("user1", MemoryTier.CORE).size());
    }

    @Test
    @Order(14)
    void getMemoryEntryCountByTier_shouldReturnCorrect() {
        MemoryEntry e1 = MemoryEntry.of("m1", MemoryType.MEMORY);
        storage.addMemoryEntry("user1", e1);
        MemoryEntry e2 = MemoryEntry.of("m2", MemoryType.MEMORY);
        storage.addMemoryEntry("user1", e2);
        assertEquals(2, storage.getMemoryEntryCountByTier("user1", MemoryTier.CORE));
    }

    @Test
    @Order(15)
    void getMemoryEntry_shouldReturnById() {
        MemoryEntry entry = MemoryEntry.of("specific", MemoryType.MEMORY);
        storage.addMemoryEntry("user1", entry);
        Optional<MemoryEntry> found = storage.getMemoryEntry("user1", entry.getId());
        assertTrue(found.isPresent());
        assertEquals("specific", found.get().getContent());
    }

    @Test
    @Order(16)
    void updateMemoryEntry_shouldReplaceWhole() {
        MemoryEntry entry = MemoryEntry.of("original", MemoryType.MEMORY);
        storage.addMemoryEntry("user1", entry);
        MemoryEntry updated = entry.withImportanceScore(0.9);
        assertTrue(storage.updateMemoryEntry("user1", updated));
        Optional<MemoryEntry> found = storage.getMemoryEntry("user1", entry.getId());
        assertTrue(found.isPresent());
        assertEquals(0.9, found.get().getImportanceScore(), 0.001);
    }

    // --- User Profile ---

    @Test
    @Order(17)
    void getUserProfile_shouldDeriveCorrectly() {
        MemoryEntry profile = MemoryEntry.of("user likes Java", MemoryType.USER_PROFILE);
        storage.addMemoryEntry("user1", profile);
        MemoryEntry memory = MemoryEntry.of("learned Spring", MemoryType.MEMORY);
        storage.addMemoryEntry("user1", memory);

        UserProfile profile1 = storage.getUserProfile("user1");
        assertEquals("user1", profile1.getUserId());
        assertEquals(1, profile1.getProfileEntries().size());
        assertEquals(1, profile1.getMemoryEntries().size());
    }

    // --- Intent Summary ---

    @Test
    @Order(18)
    void saveIntentSummary_shouldPersist() {
        Session session = storage.createSession("user1", "test");
        IntentSummary summary = IntentSummary.builder()
                .sessionId(session.getId())
                .userId("user1")
                .coreIntent("learning Java")
                .keyTopics(Arrays.asList("Java", "Spring"))
                .actionItems(Arrays.asList("read docs"))
                .emotionalTone("curious")
                .fullSummary("User wants to learn Java")
                .sourceMessageCount(5)
                .totalTokensUsed(100L)
                .build();
        storage.saveIntentSummary(summary);
        List<IntentSummary> summaries = storage.getIntentSummaries(session.getId());
        assertEquals(1, summaries.size());
        assertEquals("learning Java", summaries.get(0).getCoreIntent());
        assertEquals(Arrays.asList("Java", "Spring"), summaries.get(0).getKeyTopics());
    }

    @Test
    @Order(19)
    void getIntentSummariesByUser_shouldReturnAllForUser() {
        Session s1 = storage.createSession("user1", "test1");
        Session s2 = storage.createSession("user1", "test2");
        storage.saveIntentSummary(IntentSummary.builder()
                .sessionId(s1.getId()).userId("user1").coreIntent("intent1").build());
        storage.saveIntentSummary(IntentSummary.builder()
                .sessionId(s2.getId()).userId("user1").coreIntent("intent2").build());
        List<IntentSummary> summaries = storage.getIntentSummariesByUser("user1");
        assertEquals(2, summaries.size());
    }
}
