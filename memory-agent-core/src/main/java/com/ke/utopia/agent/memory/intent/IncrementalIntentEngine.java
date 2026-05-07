package com.ke.utopia.agent.memory.intent;

import com.ke.utopia.agent.memory.config.MemoryAgentConfig;
import com.ke.utopia.agent.memory.model.ConversationMessage;
import com.ke.utopia.agent.memory.model.IncrementalIntent;
import com.ke.utopia.agent.memory.model.IncrementalIntentResult;
import com.ke.utopia.agent.memory.model.IntentSummary;
import com.ke.utopia.agent.memory.model.MemorySnapshot;
import com.ke.utopia.agent.memory.spi.IntentSummarizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 增量意图推导引擎。
 * <p>
 * 每轮对话都使用 LLM 来识别意图和检测意图变化，
 * 能够处理上下文省略等复杂场景（如："成本呢？"）。
 * <p>
 * 如果 LLM 不可用或调用失败，则降级到规则提取。
 */
public class IncrementalIntentEngine {

    private static final Logger log = LoggerFactory.getLogger(IncrementalIntentEngine.class);

    private final IntentSummarizer intentSummarizer;
    private final RuleBasedIntentExtractor ruleExtractor;

    // 会话级别的上一轮意图缓存
    private final Map<String, IncrementalIntent> previousIntentCache = new ConcurrentHashMap<>();

    // LLM 调用失败的会话（暂时使用规则提取）
    private final Map<String, Boolean> llmFailedSessions = new ConcurrentHashMap<>();

    public IncrementalIntentEngine(MemoryAgentConfig config, IntentSummarizer intentSummarizer) {
        this.intentSummarizer = intentSummarizer;
        this.ruleExtractor = new RuleBasedIntentExtractor();
    }

    /**
     * 推导增量意图。
     * <p>
     * 策略：
     * 1. 优先使用 LLM 提取（能够处理上下文省略等复杂场景）
     * 2. 如果有 IntentSummary，从中派生并更新
     * 3. LLM 不可用时降级到规则提取
     *
     * @param sessionId   会话 ID
     * @param userMessage 当前用户消息
     * @param context     意图上下文
     * @return 增量意图
     */
    public IncrementalIntent deriveIntent(String sessionId,
                                           ConversationMessage userMessage,
                                           IntentContext context) {
        try {
            // 1. 获取上一轮增量意图
            IncrementalIntent previousIntent = previousIntentCache.get(sessionId);

            // 2. 优先尝试使用 LLM 提取
            if (intentSummarizer != null && !llmFailedSessions.containsKey(sessionId)) {
                try {
                    IncrementalIntent result = extractViaLlm(sessionId, userMessage, previousIntent, context);
                    if (result != null && !result.isEmpty()) {
                        updateCache(sessionId, result);
                        return result;
                    }
                } catch (Exception e) {
                    log.warn("LLM 提取增量意图失败，降级到规则提取：sessionId={}, error={}",
                            sessionId, e.getMessage());
                    // 标记该会话 LLM 失败，后续使用规则提取
                    llmFailedSessions.put(sessionId, true);
                }
            }

            // 3. 如果有 IntentSummary，优先从中派生
            if (context.getRecentSummaries() != null && !context.getRecentSummaries().isEmpty()) {
                IntentSummary latest = context.getRecentSummaries().get(0);
                IncrementalIntent derived = deriveFromSummary(latest, userMessage, context);
                updateCache(sessionId, derived);
                return derived;
            }

            // 4. 降级：规则提取
            IncrementalIntent ruleIntent = ruleExtractor.extract(
                    context.getSessionMessages(),
                    context.getMemorySnapshot(),
                    context.getUserProfile());

            // 5. 如果有上一轮意图，合并参数
            if (previousIntent != null && !previousIntent.isEmpty()) {
                ruleIntent = mergeIntents(previousIntent, ruleIntent);
            }

            updateCache(sessionId, ruleIntent);
            return ruleIntent;

        } catch (Exception e) {
            log.error("推导增量意图失败：sessionId={}", sessionId, e);
            return IncrementalIntent.empty();
        }
    }

    /**
     * 使用 LLM 提取增量意图。
     */
    private IncrementalIntent extractViaLlm(String sessionId,
                                              ConversationMessage userMessage,
                                              IncrementalIntent previousIntent,
                                              IntentContext context) {
        // 获取最近几轮消息作为上下文
        List<ConversationMessage> recentMessages = getRecentMessages(
                context.getSessionMessages(), 10);

        // 调用 SPI 接口
        IncrementalIntentResult result = intentSummarizer.extractIncrementalIntent(
                userMessage,
                recentMessages,
                previousIntent,
                context.getMemorySnapshot()
        );

        if (result == null) {
            log.debug("LLM 不支持增量意图提取，使用规则提取");
            return null;
        }

        // 构建增量意图
        Map<String, String> keyParams = result.getKeyParams() != null
                ? result.getKeyParams()
                : new HashMap<>();

        return IncrementalIntent.builder()
                .coreIntent(result.getCoreIntent() != null ? result.getCoreIntent() : "")
                .keyParams(keyParams)
                .source(IncrementalIntent.IntentSource.LLM_EXTRACTED)
                .confidence(result.getConfidence())
                .reasoning(result.getReasoning())
                .build();
    }

    /**
     * 从 IntentSummary 派生增量意图。
     * <p>
     * 当 LLM 返回的结果存在时，用当前消息的 LLM 结果更新 IntentSummary 的参数。
     */
    private IncrementalIntent deriveFromSummary(IntentSummary summary,
                                                 ConversationMessage userMessage,
                                                 IntentContext context) {
        // 先尝试用 LLM 更新
        if (intentSummarizer != null && !llmFailedSessions.containsKey(context)) {
            try {
                IncrementalIntent previousIntent = previousIntentCache.get(
                        userMessage.getSessionId());

                List<ConversationMessage> recentMessages = getRecentMessages(
                        context.getSessionMessages(), 10);

                IncrementalIntentResult result = intentSummarizer.extractIncrementalIntent(
                        userMessage,
                        recentMessages,
                        previousIntent,
                        context.getMemorySnapshot()
                );

                if (result != null) {
                    // 从 Summary 获取基础参数
                    Map<String, String> keyParams = new HashMap<>();

                    // 从 coreIntent 和 keyTopics 提取参数
                    extractParamsFromText(summary.getCoreIntent(), keyParams);
                    if (summary.getKeyTopics() != null) {
                        for (String topic : summary.getKeyTopics()) {
                            extractParamsFromText(topic, keyParams);
                        }
                    }

                    // 用 LLM 结果更新（优先级更高）
                    if (result.getKeyParams() != null) {
                        keyParams.putAll(result.getKeyParams());
                    }

                    return IncrementalIntent.builder()
                            .coreIntent(result.getCoreIntent() != null
                                    ? result.getCoreIntent()
                                    : summary.getCoreIntent())
                            .keyParams(keyParams)
                            .source(IncrementalIntent.IntentSource.SUMMARY_DERIVED)
                            .confidence(0.9)
                            .reasoning("从总结派生，LLM更新：" + result.getReasoning())
                            .build();
                }
            } catch (Exception e) {
                log.warn("LLM 更新意图失败，使用纯规则派生：{}", e.getMessage());
            }
        }

        // 纯规则派生
        Map<String, String> keyParams = new HashMap<>();
        extractParamsFromText(summary.getCoreIntent(), keyParams);
        if (summary.getKeyTopics() != null) {
            for (String topic : summary.getKeyTopics()) {
                extractParamsFromText(topic, keyParams);
            }
        }

        return IncrementalIntent.builder()
                .coreIntent(summary.getCoreIntent())
                .keyParams(keyParams)
                .source(IncrementalIntent.IntentSource.SUMMARY_DERIVED)
                .confidence(0.85)
                .reasoning("从意图总结派生：" + summary.getCoreIntent())
                .build();
    }

    /**
     * 从文本中提取参数（简单规则，作为补充）。
     */
    private void extractParamsFromText(String text, Map<String, String> params) {
        if (text == null) return;

        // 检测常见参数类型（通用模式）
        String lower = text.toLowerCase();

        // 时间/年份
        if (text.matches(".*\\d{4}年.*") || text.matches(".*\\d{4}.*")) {
            params.putIfAbsent("time", extractYear(text));
        }

        // 地区
        if (text.contains("北京")) params.putIfAbsent("region", "北京");
        if (text.contains("上海")) params.putIfAbsent("region", "上海");
        if (text.contains("深圳")) params.putIfAbsent("region", "深圳");
        if (text.contains("广州")) params.putIfAbsent("region", "广州");

        // 业务指标
        if (text.contains("收入")) params.putIfAbsent("metric", "收入");
        if (text.contains("成本")) params.putIfAbsent("metric", "成本");
        if (text.contains("利润")) params.putIfAbsent("metric", "利润");
        if (text.contains("销量")) params.putIfAbsent("metric", "销量");
    }

    /**
     * 提取年份。
     */
    private String extractYear(String text) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\d{4}年|(\\d{4})");
        java.util.regex.Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group().replace("年", "");
        }
        return "";
    }

    /**
     * 获取最近的消息。
     */
    private List<ConversationMessage> getRecentMessages(
            List<ConversationMessage> messages, int limit) {
        if (messages == null || messages.isEmpty()) {
            return new ArrayList<>();
        }
        int start = Math.max(0, messages.size() - limit);
        return new ArrayList<>(messages.subList(start, messages.size()));
    }

    /**
     * 合并上一轮意图和当前意图。
     */
    private IncrementalIntent mergeIntents(IncrementalIntent previous,
                                           IncrementalIntent current) {
        Map<String, String> mergedParams = new HashMap<>(previous.getKeyParams());

        // 当前参数覆盖（如果有值的话）
        for (Map.Entry<String, String> entry : current.getKeyParams().entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                mergedParams.put(entry.getKey(), entry.getValue());
            }
        }

        // 核心意图：如果当前为空，使用上一轮的
        String coreIntent = current.getCoreIntent();
        if (coreIntent == null || coreIntent.isEmpty()
                || coreIntent.equals("继续对话") || coreIntent.equals("一般对话")) {
            coreIntent = previous.getCoreIntent();
        }

        return IncrementalIntent.builder()
                .coreIntent(coreIntent)
                .keyParams(mergedParams)
                .source(IncrementalIntent.IntentSource.RULE_BASED)
                .confidence(Math.max(previous.getConfidence(), current.getConfidence()))
                .reasoning(current.getReasoning() != null
                        ? current.getReasoning()
                        : previous.getReasoning())
                .build();
    }

    /**
     * 更新缓存。
     */
    private void updateCache(String sessionId, IncrementalIntent intent) {
        if (intent != null && !intent.isEmpty()) {
            previousIntentCache.put(sessionId, intent);
        }
    }

    /**
     * 清除会话缓存（会话结束时调用）。
     */
    public void clearSession(String sessionId) {
        previousIntentCache.remove(sessionId);
        llmFailedSessions.remove(sessionId);
    }

    /**
     * 获取上一轮意图。
     */
    public Optional<IncrementalIntent> getPreviousIntent(String sessionId) {
        return Optional.ofNullable(previousIntentCache.get(sessionId));
    }
}
