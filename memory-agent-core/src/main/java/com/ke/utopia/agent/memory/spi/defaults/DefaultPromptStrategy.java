package com.ke.utopia.agent.memory.spi.defaults;

import com.ke.utopia.agent.memory.config.MemoryAgentConfig;
import com.ke.utopia.agent.memory.model.*;
import com.ke.utopia.agent.memory.spi.*;

import java.util.List;
import java.util.Optional;

/**
 * 默认 PromptStrategy 实现。
 *
 * <p>从 {@link OpenAIIntentSummarizer} 和
 * {@link com.ke.utopia.agent.memory.summary.MemoryExtractionPrompt} 中原样提取 5 段 prompt 构建逻辑，
 * 保证不提供自定义 PromptStrategy 时行为与改动前完全一致。</p>
 *
 * <p>子类可选择性覆盖任意方法，未覆盖的方法继承默认行为。</p>
 */
public class DefaultPromptStrategy implements PromptStrategy {

    private static final String DEFAULT_KEY_PARAMS_SCHEMA =
            "    \"time\": \"时间/年份\",\n" +
            "    \"region\": \"地区\",\n" +
            "    \"metric\": \"指标/主题\",\n" +
            "    \"entity\": \"实体名称\",\n" +
            "    \"action\": \"动作词\"";

    private final String keyParamsSchema;

    public DefaultPromptStrategy() {
        this(null);
    }

    public DefaultPromptStrategy(MemoryAgentConfig config) {
        this.keyParamsSchema = (config != null && config.getKeyParamsSchema() != null)
                ? config.getKeyParamsSchema()
                : DEFAULT_KEY_PARAMS_SCHEMA;
    }

    @Override
    public PromptTemplate buildSummarizePrompt(SummarizePromptContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("分析以下多轮对话，提取用户的真实意图。\n\n");

        sb.append("## 输出格式（仅 JSON，不要 markdown 代码块）\n");
        sb.append("{\"coreIntent\":\"...\",\"keyTopics\":[\"...\"],\"actionItems\":[\"...\"],");
        sb.append("\"emotionalTone\":\"...\",\"fullSummary\":\"...\"}\n\n");

        context.getUserProfile().ifPresent(profile -> {
            sb.append("## 用户画像\n");
            profile.getProfileEntries().forEach(e -> sb.append("- ").append(e.getContent()).append("\n"));
            sb.append("\n");
        });

        context.getPreviousSummaries().ifPresent(summaries -> {
            if (!summaries.isEmpty()) {
                sb.append("## 历史总结\n");
                summaries.forEach(s -> sb.append("- ").append(s.getCoreIntent()).append("\n"));
                sb.append("\n");
            }
        });

        sb.append("## 对话内容\n");
        sb.append(formatMessages(context.getMessages()));
        return PromptTemplate.of(sb.toString());
    }

    @Override
    public PromptTemplate buildCompressPrompt(CompressPromptContext context) {
        String prompt = "简洁地总结以下对话。" +
                "保留所有关键决策、事实和行动项，去除冗余交流。\n\n" +
                formatMessages(context.getMessages());
        return PromptTemplate.of(prompt);
    }

    @Override
    public PromptTemplate buildMemoryExtractionPrompt(MemoryExtractionPromptContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个记忆提取专家。分析对话内容，提取需要记住的用户信息或对话事实。\n\n");

        sb.append("## 规则\n");
        sb.append("1. 只提取明确表达或强暗示的信息。\n");
        sb.append("2. 每条提取结果应简洁、独立、完整。\n");
        sb.append("3. 为每条结果分配置信度（0.0-1.0）。\n");
        sb.append("4. 分配类别：\"preference\"（偏好）、\"fact\"（事实）、\"instruction\"（指令）、\"identity\"（身份）、\"relationship\"（关系）、\"general\"（通用）。\n");
        sb.append("5. 分类为 MEMORY（智能体备忘）或 USER_PROFILE（用户个人信息/偏好）。\n");
        sb.append("6. 不要提取下方已有画像中已存在的信息。\n");
        sb.append("7. 分配重要性评分 importanceScore（0.0-1.0）：\n");
        sb.append("   - 0.9-1.0：关键身份信息（姓名、角色、核心约束）\n");
        sb.append("   - 0.6-0.8：重要偏好、习惯或长期目标\n");
        sb.append("   - 0.3-0.5：一般事实和次要细节\n");
        sb.append("   - 0.0-0.2：临时或短期信息\n\n");

        sb.append("## 输出格式\n");
        sb.append("仅输出合法 JSON 数组（不要 markdown 代码块）：\n");
        sb.append("[\n");
        sb.append("  {\"content\":\"...\",\"type\":\"MEMORY\",\"confidence\":0.9,\"category\":\"preference\",\"importanceScore\":0.8}\n");
        sb.append("]\n\n");
        sb.append("如果没有值得记住的信息，返回空数组：[]\n\n");

        sb.append("## 已有用户画像\n");
        sb.append(formatProfile(context.getExistingProfile()));
        sb.append("\n\n");

        sb.append("## 对话内容\n");
        sb.append(formatMessages(context.getMessages()));

        return PromptTemplate.of(sb.toString());
    }

    @Override
    public PromptTemplate buildConflictDetectionPrompt(ConflictDetectionPromptContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("分析新记忆内容是否与已有记忆存在冲突。\n\n");
        sb.append("## 冲突类型\n");
        sb.append("- CONTRADICT：新内容直接与已有记忆矛盾\n");
        sb.append("- UPDATE：新内容提供了应替换旧信息的更新\n");
        sb.append("- DUPLICATE：新内容与已有记忆本质相同\n");
        sb.append("- SUPPLEMENT：新内容添加了无冲突的新信息\n\n");
        sb.append("## 解决方案\n");
        sb.append("- REPLACE_OLD：用新内容替换冲突的旧记忆\n");
        sb.append("- MERGE：将两者合并为一条记忆（提供合并内容）\n");
        sb.append("- KEEP_BOTH：无冲突，保留两条\n");
        sb.append("- DISCARD_NEW：新内容无新增价值，丢弃\n\n");
        sb.append("## 输出格式（仅 JSON，不要 markdown 代码块）\n");
        sb.append("{\"conflictType\":\"CONTRADICT|UPDATE|DUPLICATE|SUPPLEMENT\",");
        sb.append("\"resolution\":\"REPLACE_OLD|MERGE|KEEP_BOTH|DISCARD_NEW\",");
        sb.append("\"mergedContent\":\"...\",\"confidence\":0.9,\"explanation\":\"...\"");
        sb.append(",\"replacedEntryId\":\"<仅当 resolution=REPLACE_OLD 时填写要替换的条目 ID>\"}\n\n");

        sb.append("## 已有记忆\n");
        for (MemoryEntry entry : context.getExistingMemories()) {
            sb.append("- [").append(entry.getId()).append("] ").append(entry.getContent()).append("\n");
        }
        sb.append("\n## 新记忆内容\n");
        sb.append(context.getNewContent());

        return PromptTemplate.of(sb.toString());
    }

    @Override
    public PromptTemplate buildIncrementalIntentPrompt(IncrementalIntentPromptContext context) {
        StringBuilder sb = new StringBuilder();

        sb.append("你是一个意图识别专家。请分析用户的当前消息，提取核心意图和关键参数。\n\n");

        sb.append("## 分析要求\n");
        sb.append("1. 核心意图：用简练的中文描述用户想做什么（如：查询收入、修改配置、学习Python）\n");
        sb.append("2. 关键参数：提取时间、地区、指标、实体等关键信息\n");
        sb.append("3. 上下文理解：如果当前消息不完整（如\"成本呢？\"），需要从历史对话中补全参数\n");
        sb.append("4. 意图变化：判断当前消息是否改变了之前的意图\n\n");

        // 上一轮意图
        if (context.getPreviousIntent() != null && !context.getPreviousIntent().isEmpty()) {
            sb.append("## 上一轮意图\n");
            sb.append("核心意图：").append(context.getPreviousIntent().getCoreIntent()).append("\n");
            if (!context.getPreviousIntent().getKeyParams().isEmpty()) {
                sb.append("关键参数：").append(context.getPreviousIntent().getKeyParams()).append("\n");
            }
            sb.append("\n");
        }

        // 最近对话
        if (context.getRecentMessages() != null && !context.getRecentMessages().isEmpty()) {
            sb.append("## 最近对话\n");
            for (ConversationMessage msg : context.getRecentMessages()) {
                sb.append("[").append(msg.getRole().name()).append("]: ")
                  .append(msg.getContent()).append("\n");
            }
            sb.append("\n");
        }

        // 记忆快照
        if (context.getMemorySnapshot() != null && !context.getMemorySnapshot().isEmpty()) {
            sb.append("## 记忆上下文\n");
            sb.append(context.getMemorySnapshot().getMemoryBlock()).append("\n\n");
        }

        sb.append("## 当前消息\n");
        sb.append(context.getCurrentMessage().getContent()).append("\n\n");

        sb.append("## 输出格式（JSON only，no markdown）\n");
        sb.append("{\n");
        sb.append("  \"coreIntent\": \"核心意图描述\",\n");
        sb.append("  \"keyParams\": {\n");
        sb.append(keyParamsSchema);
        sb.append("\n  },\n");
        sb.append("  \"hasChanged\": true/false,\n");
        sb.append("  \"reasoning\": \"推理说明\",\n");
        sb.append("  \"confidence\": 0.95\n");
        sb.append("}");

        return PromptTemplate.of(sb.toString());
    }

    // ==================== 辅助方法（供子类复用） ====================

    /**
     * 格式化消息列表为文本。
     * 格式：[role]: content\n
     */
    protected String formatMessages(List<ConversationMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (ConversationMessage m : messages) {
            sb.append("[").append(m.getRole()).append("]: ").append(m.getContent()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 格式化用户画像为文本。
     */
    protected String formatProfile(Optional<UserProfile> profile) {
        return profile.map(p -> {
            StringBuilder sb = new StringBuilder();
            if (!p.getProfileEntries().isEmpty()) {
                sb.append("用户画像条目：\n");
                p.getProfileEntries().forEach(e -> sb.append("- ").append(e.getContent()).append("\n"));
            }
            if (!p.getMemoryEntries().isEmpty()) {
                sb.append("智能体记忆条目：\n");
                p.getMemoryEntries().forEach(e -> sb.append("- ").append(e.getContent()).append("\n"));
            }
            if (sb.length() == 0) {
                sb.append("暂无用户画像。");
            }
            return sb.toString();
        }).orElse("暂无用户画像。");
    }
}
