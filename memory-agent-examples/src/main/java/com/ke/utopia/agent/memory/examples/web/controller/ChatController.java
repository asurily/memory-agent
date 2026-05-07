package com.ke.utopia.agent.memory.examples.web.controller;

import com.ke.utopia.agent.memory.examples.web.dto.ChatRequest;
import com.ke.utopia.agent.memory.examples.web.service.ChatService;
import com.ke.utopia.agent.memory.model.IntentSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 聊天控制器。
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 对话。
     */
    @PostMapping("/message")
    public Map<String, String> chat(@RequestBody ChatRequest request) {
        log.info("Chat request: sessionId={}", request.getSessionId());

        String response = chatService.chat(request.getSessionId(), request.getContent());

        Map<String, String> result = new HashMap<>();
        result.put("content", response);
        return result;
    }

    /**
     * 触发意图总结。
     */
    @PostMapping("/summarize")
    public IntentSummary summarize(@RequestParam String sessionId) {
        log.info("Summarize request: sessionId={}", sessionId);
        return chatService.summarize(sessionId);
    }

    /**
     * 获取对话历史。
     */
    @GetMapping("/history")
    public List<?> getHistory(@RequestParam String sessionId) {
        return chatService.getHistory(sessionId);
    }
}
