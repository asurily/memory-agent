package com.ke.utopia.agent.memory.conversation;

import com.ke.utopia.agent.memory.exception.SessionNotFoundException;
import com.ke.utopia.agent.memory.model.*;
import com.ke.utopia.agent.memory.spi.MemoryStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 会话管理器：管理会话生命周期和消息。
 */
public final class ConversationManager {

    private static final Logger log = LoggerFactory.getLogger(ConversationManager.class);

    private final MemoryStorage storage;

    public ConversationManager(MemoryStorage storage) {
        this.storage = storage;
    }

    public Session createSession(String userId, String source) {
        Session session = storage.createSession(userId, source);
        log.debug("Created session {} for user {}", session.getId(), userId);
        return session;
    }

    public Optional<Session> getSession(String sessionId) {
        return storage.getSession(sessionId);
    }

    public List<Session> listSessions(String userId) {
        return storage.getSessionsByUser(userId);
    }

    public List<Session> listSessions(String userId, SessionStatus status) {
        return storage.getSessionsByUser(userId).stream()
                .filter(s -> s.getStatus() == status)
                .collect(Collectors.toList());
    }

    public Session closeSession(String sessionId) {
        Session session = storage.getSession(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));
        storage.closeSession(sessionId);
        log.debug("Closed session {}", sessionId);
        return storage.getSession(sessionId).orElse(session);
    }

    public ConversationMessage addUserMessage(String sessionId, String content) {
        validateSession(sessionId);
        ConversationMessage msg = ConversationMessage.userMessage(sessionId, content);
        storage.addMessage(msg);
        incrementMessageCount(sessionId);
        log.debug("Added user message to session {}, total messages: {}",
                sessionId, storage.getMessageCount(sessionId));
        return msg;
    }

    public ConversationMessage addAssistantMessage(String sessionId, String content) {
        validateSession(sessionId);
        ConversationMessage msg = ConversationMessage.assistantMessage(sessionId, content);
        storage.addMessage(msg);
        incrementMessageCount(sessionId);
        return msg;
    }

    public ConversationMessage addToolMessage(String sessionId, String content,
                                               String toolCallId, String toolName) {
        validateSession(sessionId);
        ConversationMessage msg = ConversationMessage.toolMessage(sessionId, content, toolCallId, toolName);
        storage.addMessage(msg);
        incrementMessageCount(sessionId);
        return msg;
    }

    public List<ConversationMessage> getMessages(String sessionId) {
        return storage.getMessages(sessionId);
    }

    public List<ConversationMessage> getRecentMessages(String sessionId, int limit) {
        return storage.getRecentMessages(sessionId, limit);
    }

    public int getMessageCount(String sessionId) {
        return storage.getMessageCount(sessionId);
    }

    public List<ConversationMessage> searchMessages(String userId, String query, int limit) {
        String lowerQuery = query.toLowerCase();
        return storage.getSessionsByUser(userId).stream()
                .flatMap(s -> storage.getMessages(s.getId()).stream())
                .filter(m -> m.getContent() != null && m.getContent().toLowerCase().contains(lowerQuery))
                .limit(limit)
                .collect(Collectors.toList());
    }

    private void validateSession(String sessionId) {
        storage.getSession(sessionId).orElseThrow(() -> new SessionNotFoundException(sessionId));
    }

    private void incrementMessageCount(String sessionId) {
        Session session = storage.getSession(sessionId).orElse(null);
        if (session != null) {
            storage.updateSession(session.incrementMessageCount());
        }
    }
}
