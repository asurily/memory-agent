package com.ke.utopia.agent.memory.exception;

public class SecurityBlockedException extends MemoryAgentException {
    private final String threatType;

    public SecurityBlockedException(String threatType, String message) {
        super(message);
        this.threatType = threatType;
    }

    public String getThreatType() { return threatType; }
}
