package com.ke.utopia.agent.memory.exception;

public class MemoryAgentException extends RuntimeException {
    public MemoryAgentException(String message) {
        super(message);
    }
    public MemoryAgentException(String message, Throwable cause) {
        super(message, cause);
    }
}
