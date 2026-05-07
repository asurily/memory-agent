package com.ke.utopia.agent.memory.exception;

public class SessionNotFoundException extends MemoryAgentException {
    public SessionNotFoundException(String sessionId) {
        super("Session not found: " + sessionId);
    }
}
