package com.ke.utopia.agent.memory.examples.web.service;

import com.ke.utopia.agent.memory.MemoryAgent;
import com.ke.utopia.agent.memory.model.ConversationMessage;
import com.ke.utopia.agent.memory.model.IntentSummary;
import com.ke.utopia.agent.memory.model.MemoryEntry;
import com.ke.utopia.agent.memory.model.MemoryType;
import com.ke.utopia.agent.memory.model.Session;
import com.ke.utopia.agent.memory.model.TurnContext;
import com.ke.utopia.agent.memory.model.UserProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 聊天服务。
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final MemoryAgent memoryAgent;

    public ChatService(MemoryAgent memoryAgent) {
        this.memoryAgent = memoryAgent;
    }

    /**
     * 创建会话。
     */
    public Session createSession(String userId) {
        return memoryAgent.createSession(userId, "web-ui");
    }

    /**
     * 获取会话。
     */
    public Session getSession(String sessionId) {
        return memoryAgent.getSession(sessionId).orElse(null);
    }

    /**
     * 关闭会话。
     */
    public Session closeSession(String sessionId) {
        return memoryAgent.closeSession(sessionId);
    }

    /**
     * 获取用户会话列表。
     */
    public List<Session> getSessionsByUser(String userId) {
        return memoryAgent.listSessions(userId);
    }

    /**
     * 获取对话历史。
     */
    public List<ConversationMessage> getHistory(String sessionId) {
        return memoryAgent.getMessages(sessionId);
    }

    /**
     * 处理用户消息：一站式获取多轮对话精确上下文，返回记忆上下文前缀。
     *
     * <p>启用 autoPipelineMode=ASYNC 后，总结/压缩/记忆提取均在内部自动触发，
     * 调用方无需手动编排。如需可选等待总结结果：</p>
     * <pre>
     *   ctx.getPendingSummarization().ifPresent(f ->
     *       f.thenAccept(summary -> log.info("Summarized: {}", summary.getCoreIntent())));
     * </pre>
     */
    public String chat(String sessionId, String content) {
        TurnContext ctx = memoryAgent.processUserMessage(sessionId, content);
        return ctx.getMemoryContextPrefix();
        // 总结/压缩/记忆提取 已在内部自动触发，无需手动编排
    }

    /**
     * 触发意图总结。
     */
    public IntentSummary summarize(String sessionId) {
        return memoryAgent.summarize(sessionId);
    }

    /**
     * 获取用户记忆。
     */
    public List<MemoryEntry> getMemories(String userId) {
        List<MemoryEntry> memories = memoryAgent.getMemories(userId, MemoryType.MEMORY);
        List<MemoryEntry> profile = memoryAgent.getMemories(userId, MemoryType.USER_PROFILE);
        List<MemoryEntry> all = new java.util.ArrayList<>(memories);
        all.addAll(profile);
        return all;
    }

    /**
     * 获取用户画像。
     */
    public UserProfile getUserProfile(String userId) {
        return memoryAgent.getUserProfile(userId);
    }
}
