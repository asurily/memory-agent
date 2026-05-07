package com.ke.utopia.agent.memory.examples.web.dto;

/**
 * 聊天请求 DTO。
 */
public class ChatRequest {

    private String sessionId;
    private String userId;
    private String content;

    public ChatRequest() {
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
