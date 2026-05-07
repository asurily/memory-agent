package com.ke.utopia.agent.memory.exception;

public class MemoryCapacityExceededException extends MemoryAgentException {
    private final int currentChars;
    private final int limit;

    public MemoryCapacityExceededException(int currentChars, int limit) {
        super("Memory capacity exceeded: " + currentChars + " > " + limit + " chars");
        this.currentChars = currentChars;
        this.limit = limit;
    }

    public int getCurrentChars() { return currentChars; }
    public int getLimit() { return limit; }
}
