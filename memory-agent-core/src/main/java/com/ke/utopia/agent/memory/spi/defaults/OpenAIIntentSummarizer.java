package com.ke.utopia.agent.memory.spi.defaults;

import com.ke.utopia.agent.memory.config.MemoryAgentConfig;
import com.ke.utopia.agent.memory.exception.SummarizationException;
import com.ke.utopia.agent.memory.model.*;
import com.ke.utopia.agent.memory.spi.*;
import com.ke.utopia.agent.memory.spi.IntentSummarizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 默认 LLM 总结器，调用 OpenAI 兼容 API。
 * 使用 java.net.http.HttpClient（Java 11+），零外部依赖。
 */
public final class OpenAIIntentSummarizer implements IntentSummarizer {

    private static final Logger log = LoggerFactory.getLogger(OpenAIIntentSummarizer.class);

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int maxTokens;
    private final double temperature;
    private final HttpClient httpClient;
    private final PromptStrategy promptStrategy;

    public OpenAIIntentSummarizer(MemoryAgentConfig config) {
        this(config, null);
    }

    public OpenAIIntentSummarizer(MemoryAgentConfig config, PromptStrategy promptStrategy) {
        this.apiKey = config.getLlmApiKey();
        this.baseUrl = config.getLlmBaseUrl();
        this.model = config.getLlmModel();
        this.maxTokens = config.getLlmMaxTokens();
        this.temperature = config.getLlmTemperature();
        this.httpClient = buildHttpClient(config.getLlmTimeoutSeconds(),
                config.getLlmProxyHost(), config.getLlmProxyPort());
        this.promptStrategy = promptStrategy != null ? promptStrategy : new DefaultPromptStrategy(config);
        log.info("OpenAIIntentSummarizer initialized: baseUrl={}, model={}, proxy={}",
                baseUrl, model, config.hasProxy() ? config.getLlmProxyHost() + ":" + config.getLlmProxyPort() : "none");
    }

    public OpenAIIntentSummarizer(String apiKey, String baseUrl, String model,
                                   int maxTokens, double temperature, int timeoutSeconds) {
        this(apiKey, baseUrl, model, maxTokens, temperature, timeoutSeconds, null, 0);
    }

    public OpenAIIntentSummarizer(String apiKey, String baseUrl, String model,
                                   int maxTokens, double temperature, int timeoutSeconds,
                                   String proxyHost, int proxyPort) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.httpClient = buildHttpClient(timeoutSeconds, proxyHost, proxyPort);
        this.promptStrategy = new DefaultPromptStrategy();
    }

    private static HttpClient buildHttpClient(int timeoutSeconds, String proxyHost, int proxyPort) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds));

        if (proxyHost != null && !proxyHost.isEmpty() && proxyPort > 0) {
            builder.proxy(ProxySelector.of(new InetSocketAddress(proxyHost, proxyPort)));
        }

        return builder.build();
    }

    @Override
    public IntentSummary summarize(String userId, String sessionId,
                                    List<ConversationMessage> messages,
                                    Optional<UserProfile> userProfile,
                                    Optional<List<IntentSummary>> previousSummaries) {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new SummarizationException("LLM API key not configured");
        }

        SummarizePromptContext context = new SummarizePromptContext(
                userId, sessionId, messages, userProfile, previousSummaries);
        PromptTemplate template = promptStrategy.buildSummarizePrompt(context);
        String responseBody = callLlmApi(template);
        return parseResponse(responseBody, userId, sessionId, messages.size());
    }

    @Override
    public CompletableFuture<IntentSummary> summarizeAsync(String userId, String sessionId,
                                                            List<ConversationMessage> messages,
                                                            Optional<UserProfile> userProfile,
                                                            Optional<List<IntentSummary>> previousSummaries) {
        return CompletableFuture.supplyAsync(() ->
                summarize(userId, sessionId, messages, userProfile, previousSummaries));
    }

    @Override
    public String compressConversation(List<ConversationMessage> messages, int targetTokenBudget) {
        CompressPromptContext context = new CompressPromptContext(messages, targetTokenBudget);
        PromptTemplate template = promptStrategy.buildCompressPrompt(context);
        return callLlmApi(template);
    }

    @Override
    public String getModelIdentifier() {
        return model;
    }

    private String callLlmApi(String prompt) {
        return callLlmApi(PromptTemplate.of(prompt));
    }

    private String callLlmApi(PromptTemplate template) {
        try {
            String requestBody = template.hasSystemMessage()
                    ? buildRequestBodyWithSystem(template.getSystemMessage(), template.getUserMessage())
                    : buildRequestBody(template.getUserMessage());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new SummarizationException("LLM API returned status " + response.statusCode() +
                        ": " + response.body());
            }

            return extractContentFromResponse(response.body());
        } catch (SummarizationException e) {
            throw e;
        } catch (Exception e) {
            throw new SummarizationException("Failed to call LLM API", e);
        }
    }

    private String buildRequestBody(String prompt) {
        // 手动构建 JSON（避免强制依赖 Jackson）
        String escapedPrompt = prompt
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\t", "\\t");

        return "{\"model\":\"" + model + "\"," +
                "\"messages\":[{\"role\":\"user\",\"content\":\"" + escapedPrompt + "\"}]," +
                "\"max_tokens\":" + maxTokens + "," +
                "\"temperature\":" + temperature + "}";
    }

    private String buildRequestBodyWithSystem(String systemMessage, String userMessage) {
        String escapedSystem = escapeJson(systemMessage);
        String escapedUser = escapeJson(userMessage);

        return "{\"model\":\"" + model + "\"," +
                "\"messages\":[" +
                "{\"role\":\"system\",\"content\":\"" + escapedSystem + "\"}," +
                "{\"role\":\"user\",\"content\":\"" + escapedUser + "\"}" +
                "]," +
                "\"max_tokens\":" + maxTokens + "," +
                "\"temperature\":" + temperature + "}";
    }

    private String extractContentFromResponse(String responseBody) {
        // 简单 JSON 解析（避免强制依赖 Jackson）
        // 定位 "content" 字段值
        String contentMarker = "\"content\"";
        int contentIdx = responseBody.indexOf(contentMarker);
        if (contentIdx == -1) {
            throw new SummarizationException("Unexpected LLM response format: " + responseBody);
        }

        int quoteStart = responseBody.indexOf("\"", contentIdx + contentMarker.length());
        if (quoteStart == -1) {
            throw new SummarizationException("Cannot find content value in LLM response");
        }

        // 找到内容字符串的结束引号（处理转义）
        StringBuilder content = new StringBuilder();
        int i = quoteStart + 1;
        while (i < responseBody.length()) {
            char c = responseBody.charAt(i);
            if (c == '\\') {
                i++;
                if (i < responseBody.length()) {
                    char next = responseBody.charAt(i);
                    switch (next) {
                        case 'n': content.append('\n'); break;
                        case 't': content.append('\t'); break;
                        case '"': content.append('"'); break;
                        case '\\': content.append('\\'); break;
                        default: content.append(next);
                    }
                }
            } else if (c == '"') {
                break;
            } else {
                content.append(c);
            }
            i++;
        }

        return content.toString();
    }

    private IntentSummary parseResponse(String llmResponse, String userId,
                                          String sessionId, int messageCount) {
        try {
            // 提取 JSON 部分（可能被包裹在 markdown code block 中）
            String json = llmResponse.trim();
            if (json.contains("```")) {
                int start = json.indexOf("{");
                int end = json.lastIndexOf("}") + 1;
                if (start >= 0 && end > start) {
                    json = json.substring(start, end);
                }
            }

            // 简单 JSON 字段提取
            String coreIntent = extractJsonString(json, "coreIntent");
            List<String> keyTopics = extractJsonArray(json, "keyTopics");
            List<String> actionItems = extractJsonArray(json, "actionItems");
            String emotionalTone = extractJsonString(json, "emotionalTone");
            String fullSummary = extractJsonString(json, "fullSummary");

            return IntentSummary.builder()
                    .sessionId(sessionId)
                    .userId(userId)
                    .coreIntent(coreIntent != null ? coreIntent : "Unable to determine intent")
                    .keyTopics(keyTopics)
                    .actionItems(actionItems)
                    .emotionalTone(emotionalTone != null ? emotionalTone : "neutral")
                    .fullSummary(fullSummary != null ? fullSummary : "")
                    .sourceMessageCount(messageCount)
                    .build();
        } catch (Exception e) {
            throw new SummarizationException("Failed to parse LLM response: " + llmResponse, e);
        }
    }

    private String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx == -1) return null;

        int colonIdx = json.indexOf(":", idx + pattern.length());
        if (colonIdx == -1) return null;

        int quoteStart = json.indexOf("\"", colonIdx);
        if (quoteStart == -1) return null;

        StringBuilder value = new StringBuilder();
        int i = quoteStart + 1;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\') {
                i++;
                if (i < json.length()) {
                    char next = json.charAt(i);
                    switch (next) {
                        case 'n': value.append('\n'); break;
                        case '"': value.append('"'); break;
                        case '\\': value.append('\\'); break;
                        default: value.append(next);
                    }
                }
            } else if (c == '"') {
                break;
            } else {
                value.append(c);
            }
            i++;
        }
        return value.toString();
    }

    private List<String> extractJsonArray(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx == -1) return Collections.emptyList();

        int bracketStart = json.indexOf("[", idx);
        if (bracketStart == -1) return Collections.emptyList();

        int bracketEnd = json.indexOf("]", bracketStart);
        if (bracketEnd == -1) return Collections.emptyList();

        String arrayContent = json.substring(bracketStart + 1, bracketEnd);
        List<String> items = new ArrayList<>();
        for (String item : arrayContent.split(",")) {
            item = item.trim();
            if (item.startsWith("\"") && item.endsWith("\"")) {
                items.add(item.substring(1, item.length() - 1));
            }
        }
        return items;
    }

    @Override
    public List<MemoryExtraction> extractMemories(List<ConversationMessage> messages,
                                                    Optional<UserProfile> existingProfile) {
        if (apiKey == null || apiKey.isEmpty()) {
            return Collections.emptyList();
        }

        MemoryExtractionPromptContext context = new MemoryExtractionPromptContext(messages, existingProfile);
        PromptTemplate template = promptStrategy.buildMemoryExtractionPrompt(context);
        String responseBody = callLlmApi(template);
        return parseExtractionResponse(responseBody);
    }

    private List<MemoryExtraction> parseExtractionResponse(String llmResponse) {
        try {
            String json = llmResponse.trim();
            if (json.contains("```")) {
                int start = json.indexOf("[");
                int end = json.lastIndexOf("]") + 1;
                if (start >= 0 && end > start) {
                    json = json.substring(start, end);
                }
            }

            if (!json.startsWith("[")) {
                return Collections.emptyList();
            }

            List<MemoryExtraction> result = new ArrayList<>();
            // Simple JSON array parsing for [{...},{...}]
            int i = 1; // skip '['
            while (i < json.length()) {
                int objStart = json.indexOf("{", i);
                if (objStart == -1) break;

                int objEnd = findMatchingBrace(json, objStart);
                if (objEnd == -1) break;

                String obj = json.substring(objStart, objEnd + 1);
                String content = extractJsonString(obj, "content");
                String typeStr = extractJsonString(obj, "type");
                String confidenceStr = extractJsonNumber(obj, "confidence");
                String category = extractJsonString(obj, "category");
                String importanceStr = extractJsonNumber(obj, "importanceScore");

                if (content != null) {
                    MemoryType type = "USER_PROFILE".equals(typeStr) ? MemoryType.USER_PROFILE : MemoryType.MEMORY;
                    double confidence = confidenceStr != null ? Double.parseDouble(confidenceStr) : 0.5;
                    double importanceScore = importanceStr != null ? Double.parseDouble(importanceStr) : 0.5;
                    result.add(new MemoryExtraction(content, type, confidence, category, importanceScore));
                }

                i = objEnd + 1;
            }

            return result;
        } catch (Exception e) {
            log.warn("Failed to parse memory extraction response: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private int findMatchingBrace(String json, int start) {
        int depth = 0;
        for (int i = start; i < json.length(); i++) {
            if (json.charAt(i) == '{') depth++;
            else if (json.charAt(i) == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private String extractJsonNumber(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx == -1) return null;

        int colonIdx = json.indexOf(":", idx + pattern.length());
        if (colonIdx == -1) return null;

        int start = colonIdx + 1;
        while (start < json.length() && json.charAt(start) == ' ') start++;

        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.')) {
            end++;
        }
        return json.substring(start, end);
    }

    @Override
    public ConflictResolution detectConflict(String newContent, List<MemoryEntry> existing) {
        if (apiKey == null || apiKey.isEmpty()) {
            return new ConflictResolution(ConflictResolution.ConflictType.SUPPLEMENT,
                    ConflictResolution.ResolutionAction.KEEP_BOTH, null, 1.0,
                    "No LLM configured for conflict detection");
        }

        if (existing.isEmpty()) {
            return new ConflictResolution(ConflictResolution.ConflictType.SUPPLEMENT,
                    ConflictResolution.ResolutionAction.KEEP_BOTH, null, 1.0,
                    "No existing memories to conflict with");
        }

        ConflictDetectionPromptContext context = new ConflictDetectionPromptContext(newContent, existing);
        PromptTemplate template = promptStrategy.buildConflictDetectionPrompt(context);
        String responseBody = callLlmApi(template);
        return parseConflictResponse(responseBody);
    }

    private ConflictResolution parseConflictResponse(String llmResponse) {
        try {
            String json = llmResponse.trim();
            if (json.contains("```")) {
                int start = json.indexOf("{");
                int end = json.lastIndexOf("}") + 1;
                if (start >= 0 && end > start) {
                    json = json.substring(start, end);
                }
            }

            String typeStr = extractJsonString(json, "conflictType");
            String actionStr = extractJsonString(json, "resolution");
            String merged = extractJsonString(json, "mergedContent");
            String confStr = extractJsonNumber(json, "confidence");
            String explanation = extractJsonString(json, "explanation");
            String replacedEntryId = extractJsonString(json, "replacedEntryId");

            ConflictResolution.ConflictType type = parseConflictType(typeStr);
            ConflictResolution.ResolutionAction action = parseResolutionAction(actionStr);
            double confidence = confStr != null ? Double.parseDouble(confStr) : 0.5;

            return new ConflictResolution(type, action, merged, confidence, explanation, replacedEntryId);
        } catch (Exception e) {
            log.warn("Failed to parse conflict detection response: {}", e.getMessage());
            return new ConflictResolution(ConflictResolution.ConflictType.SUPPLEMENT,
                    ConflictResolution.ResolutionAction.KEEP_BOTH, null, 0.5,
                    "Failed to parse LLM conflict response");
        }
    }

    private ConflictResolution.ConflictType parseConflictType(String str) {
        if (str == null) return ConflictResolution.ConflictType.SUPPLEMENT;
        try {
            return ConflictResolution.ConflictType.valueOf(str);
        } catch (IllegalArgumentException e) {
            return ConflictResolution.ConflictType.SUPPLEMENT;
        }
    }

    private ConflictResolution.ResolutionAction parseResolutionAction(String str) {
        if (str == null) return ConflictResolution.ResolutionAction.KEEP_BOTH;
        try {
            return ConflictResolution.ResolutionAction.valueOf(str);
        } catch (IllegalArgumentException e) {
            return ConflictResolution.ResolutionAction.KEEP_BOTH;
        }
    }

    // ==================== Chat API Methods (for Web UI) ====================

    /**
     * 同步调用 LLM 进行对话。
     */
    @Override
    public String callChat(List<ConversationMessage> messages, Optional<String> systemPrompt) {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new SummarizationException("LLM API key not configured");
        }

        String requestBody = buildChatRequestBody(messages, systemPrompt);
        return callLlmApiWithBody(requestBody);
    }

    /**
     * 构建 Chat API 请求体。
     */
    private String buildChatRequestBody(List<ConversationMessage> messages,
                                         Optional<String> systemPrompt) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"model\":\"").append(model).append("\",");
        sb.append("\"messages\":[");

        boolean hasSystemPrompt = systemPrompt.isPresent();
        boolean hasMessages = messages != null && !messages.isEmpty();

        // 添加 system prompt
        if (hasSystemPrompt) {
            String escapedSystem = escapeJson(systemPrompt.get());
            sb.append("{\"role\":\"system\",\"content\":\"").append(escapedSystem).append("\"}");
            if (hasMessages) {
                sb.append(",");
            }
        }

        // 添加对话消息
        if (hasMessages) {
            for (int i = 0; i < messages.size(); i++) {
                ConversationMessage m = messages.get(i);
                String escapedContent = escapeJson(m.getContent());
                sb.append("{\"role\":\"").append(m.getRole().value()).append("\",");
                sb.append("\"content\":\"").append(escapedContent).append("\"}");
                if (i < messages.size() - 1) {
                    sb.append(",");
                }
            }
        }

        sb.append("],");
        sb.append("\"max_tokens\":").append(maxTokens).append(",");
        sb.append("\"temperature\":").append(temperature);
        sb.append("}");

        return sb.toString();
    }

    /**
     * 使用自定义请求体调用 LLM API。
     */
    private String callLlmApiWithBody(String requestBody) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new SummarizationException("LLM API returned status " + response.statusCode() +
                        ": " + response.body());
            }

            return extractContentFromResponse(response.body());
        } catch (SummarizationException e) {
            throw e;
        } catch (Exception e) {
            throw new SummarizationException("Failed to call LLM API", e);
        }
    }

    /**
     * 转义 JSON 字符串。
     */
    private String escapeJson(String text) {
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    // ==================== Incremental Intent Methods ====================

    /**
     * 提取增量意图（每轮对话实时推导）。
     * <p>
     * 与 summarize() 不同，此方法不需要累积大量消息即可触发，
     * 能够处理上下文省略等复杂场景。
     * <p>
     * 示例：
     * <ul>
     *   <li>第1轮："2026年北京大区的收入怎样" → intent=查询收入, params={time:2026, region:北京, metric:收入}</li>
     *   <li>第2轮："成本呢？" → intent=查询成本, params={time:2026, region:北京, metric:成本}</li>
     * </ul>
     */
    @Override
    public IncrementalIntentResult extractIncrementalIntent(
            ConversationMessage currentMessage,
            List<ConversationMessage> recentMessages,
            IncrementalIntent previousIntent,
            MemorySnapshot memorySnapshot) {
        if (apiKey == null || apiKey.isEmpty()) {
            log.debug("LLM API key not configured, incremental intent extraction not supported");
            return null;
        }

        try {
            IncrementalIntentPromptContext context = new IncrementalIntentPromptContext(
                    currentMessage, recentMessages, previousIntent, memorySnapshot);
            PromptTemplate template = promptStrategy.buildIncrementalIntentPrompt(context);
            String responseBody = callLlmApi(template);
            return parseIncrementalIntentResponse(responseBody, currentMessage);
        } catch (Exception e) {
            log.warn("Failed to extract incremental intent: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析增量意图响应。
     */
    private IncrementalIntentResult parseIncrementalIntentResponse(String llmResponse,
                                                                    ConversationMessage currentMessage) {
        try {
            String json = llmResponse.trim();
            if (json.contains("```")) {
                int start = json.indexOf("{");
                int end = json.lastIndexOf("}") + 1;
                if (start >= 0 && end > start) {
                    json = json.substring(start, end);
                }
            }

            String coreIntent = extractJsonString(json, "coreIntent");
            boolean hasChanged = extractJsonBoolean(json, "hasChanged");
            String reasoning = extractJsonString(json, "reasoning");
            String confidenceStr = extractJsonNumber(json, "confidence");
            double confidence = confidenceStr != null ? Double.parseDouble(confidenceStr) : 0.7;

            // 提取 keyParams 对象
            Map<String, String> keyParams = extractJsonObject(json, "keyParams");

            return new IncrementalIntentResult(
                    coreIntent != null ? coreIntent : "",
                    keyParams,
                    hasChanged,
                    reasoning != null ? reasoning : "",
                    confidence
            );
        } catch (Exception e) {
            log.warn("Failed to parse incremental intent response: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 提取 JSON 对象（keyParams）。
     */
    private Map<String, String> extractJsonObject(String json, String key) {
        Map<String, String> result = new HashMap<>();
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx == -1) return result;

        int braceStart = json.indexOf("{", idx);
        if (braceStart == -1) return result;

        int braceEnd = findMatchingBrace(json, braceStart);
        if (braceEnd == -1) return result;

        String objContent = json.substring(braceStart + 1, braceEnd);

        // 简单解析 key-value 对
        String[] pairs = objContent.split(",");
        for (String pair : pairs) {
            int colonIdx = pair.indexOf(":");
            if (colonIdx == -1) continue;

            String k = pair.substring(0, colonIdx).trim();
            String v = pair.substring(colonIdx + 1).trim();

            if (k.startsWith("\"") && k.endsWith("\"")) {
                k = k.substring(1, k.length() - 1);
            }
            if (v.startsWith("\"") && v.endsWith("\"")) {
                v = v.substring(1, v.length() - 1);
            }

            if (!k.isEmpty() && !v.isEmpty()) {
                result.put(k, v);
            }
        }

        return result;
    }

    /**
     * 提取 JSON 布尔值。
     */
    private boolean extractJsonBoolean(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx == -1) return false;

        int colonIdx = json.indexOf(":", idx + pattern.length());
        if (colonIdx == -1) return false;

        int start = colonIdx + 1;
        while (start < json.length() && json.charAt(start) == ' ') start++;

        if (json.substring(start).startsWith("true")) {
            return true;
        }
        return false;
    }
}
