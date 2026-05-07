package com.ke.utopia.agent.memory.examples.web.controller;

import com.ke.utopia.agent.memory.examples.web.service.ChatService;
import com.ke.utopia.agent.memory.model.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话管理控制器。
 */
@RestController
@RequestMapping("/api/session")
public class SessionController {

    private static final Logger log = LoggerFactory.getLogger(SessionController.class);

    private final ChatService chatService;

    public SessionController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 获取用户会话列表。
     */
    @GetMapping("/list/{userId}")
    public List<Session> getSessionsByUser(@PathVariable String userId) {
        log.info("Get sessions request: userId={}", userId);
        return chatService.getSessionsByUser(userId);
    }

    /**
     * 创建会话。
     */
    @PostMapping
    public Map<String, Object> createSession(@RequestParam String userId) {
        log.info("Create session request: userId={}", userId);

        Session session = chatService.createSession(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", session.getId());
        result.put("userId", session.getUserId());
        result.put("createdAt", session.getStartedAt().toString());
        result.put("status", session.getStatus().name());
        return result;
    }

    /**
     * 获取会话信息。
     */
    @GetMapping("/{sessionId}")
    public Map<String, Object> getSession(@PathVariable String sessionId) {
        log.info("Get session request: sessionId={}", sessionId);

        Session session = chatService.getSession(sessionId);
        if (session == null) {
            Map<String, Object> notFound = new HashMap<>();
            notFound.put("error", "Session not found");
            return notFound;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", session.getId());
        result.put("userId", session.getUserId());
        result.put("createdAt", session.getStartedAt().toString());
        result.put("status", session.getStatus().name());
        return result;
    }

    /**
     * 关闭会话。
     */
    @DeleteMapping("/{sessionId}")
    public Map<String, Object> closeSession(@PathVariable String sessionId) {
        log.info("Close session request: sessionId={}", sessionId);

        Session session = chatService.closeSession(sessionId);

        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", session.getId());
        result.put("status", session.getStatus().name());
        return result;
    }
}
