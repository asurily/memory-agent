package com.ke.utopia.agent.memory.intent;

import com.ke.utopia.agent.memory.model.ConversationMessage;
import com.ke.utopia.agent.memory.model.IncrementalIntent;
import com.ke.utopia.agent.memory.model.MemorySnapshot;
import com.ke.utopia.agent.memory.model.UserProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于规则的意图提取器（降级方案）。
 * <p>
 * 当 LLM 不可用时使用的简单规则提取，仅用于应急降级。
 */
public class RuleBasedIntentExtractor {

    private static final Logger log = LoggerFactory.getLogger(RuleBasedIntentExtractor.class);

    // 简单动作词识别
    private static final Map<Pattern, String> ACTION_PATTERNS = Map.of(
            Pattern.compile("(创建|搭建|构建|开发|编写|实现|建立)"), "创建",
            Pattern.compile("(学习|了解|掌握|研究)"), "学习",
            Pattern.compile("(改为|替换|切换|修改|变更)"), "修改",
            Pattern.compile("(不用|不要|删除|移除|取消)"), "删除",
            Pattern.compile("(怎么|如何|什么是|哪个)"), "查询"
    );

    // 简单实体提取（引用内容、英文专有名词）
    private static final List<Pattern> ENTITY_PATTERNS = List.of(
            Pattern.compile("\"([^\"]{2,15})\""),
            Pattern.compile("\u300c([^\u300d]{2,15})\u300d"),
            Pattern.compile("'([^']{2,15})'"),
            Pattern.compile("\\b([A-Z][a-zA-Z0-9]{2,})\\b")
    );

    public RuleBasedIntentExtractor() {
    }

    /**
     * 简单规则提取（降级方案）。
     */
    public IncrementalIntent extract(List<ConversationMessage> messages,
                                      MemorySnapshot memorySnapshot,
                                      UserProfile userProfile) {
        String lastMessage = getLastUserMessage(messages);
        if (lastMessage == null || lastMessage.isEmpty()) {
            return IncrementalIntent.empty();
        }

        // 提取动作
        String action = detectAction(lastMessage);

        // 提取实体
        Set<String> entities = extractEntities(lastMessage);

        // 构建参数
        Map<String, String> params = new HashMap<>();
        if (!entities.isEmpty()) {
            params.put("entities", String.join(", ", entities));
        }

        // 构建核心意图
        String coreIntent = action;
        if (!entities.isEmpty()) {
            coreIntent += "：" + String.join("、", entities);
        }

        return IncrementalIntent.builder()
                .coreIntent(coreIntent)
                .keyParams(params)
                .source(IncrementalIntent.IntentSource.RULE_BASED)
                .confidence(0.4)
                .reasoning("规则降级提取")
                .build();
    }

    private String getLastUserMessage(List<ConversationMessage> messages) {
        if (messages == null) return null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            ConversationMessage msg = messages.get(i);
            if (msg.getRole() == com.ke.utopia.agent.memory.model.MessageRole.USER) {
                return msg.getContent();
            }
        }
        return null;
    }

    private String detectAction(String message) {
        for (Map.Entry<Pattern, String> entry : ACTION_PATTERNS.entrySet()) {
            if (entry.getKey().matcher(message).find()) {
                return entry.getValue();
            }
        }
        return "继续对话";
    }

    private Set<String> extractEntities(String message) {
        Set<String> entities = new LinkedHashSet<>();
        for (Pattern pattern : ENTITY_PATTERNS) {
            Matcher matcher = pattern.matcher(message);
            while (matcher.find()) {
                String entity = matcher.group(1).trim();
                if (entity.length() >= 2) {
                    entities.add(entity);
                }
            }
        }
        return entities;
    }
}
