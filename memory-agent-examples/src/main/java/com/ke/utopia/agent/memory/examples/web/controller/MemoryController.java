package com.ke.utopia.agent.memory.examples.web.controller;

import com.ke.utopia.agent.memory.examples.web.service.ChatService;
import com.ke.utopia.agent.memory.model.MemoryEntry;
import com.ke.utopia.agent.memory.model.UserProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 记忆和画像查询控制器。
 */
@RestController
@RequestMapping("/api")
public class MemoryController {

    private static final Logger log = LoggerFactory.getLogger(MemoryController.class);

    private final ChatService chatService;

    public MemoryController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 获取用户记忆。
     */
    @GetMapping("/memory/{userId}")
    public Map<String, Object> getMemories(@PathVariable String userId) {
        log.info("Get memories request: userId={}", userId);

        List<MemoryEntry> memories = chatService.getMemories(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("count", memories.size());
        result.put("memories", memories);
        return result;
    }

    /**
     * 获取用户画像。
     */
    @GetMapping("/profile/{userId}")
    public Map<String, Object> getProfile(@PathVariable String userId) {
        log.info("Get profile request: userId={}", userId);

        UserProfile profile = chatService.getUserProfile(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        if (profile != null) {
            result.put("profileEntries", profile.getProfileEntries());
        } else {
            result.put("profileEntries", List.of());
        }
        return result;
    }
}
