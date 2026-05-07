package com.ke.utopia.agent.memory.summary;

import com.ke.utopia.agent.memory.model.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 构造发送给 LLM 的意图总结 Prompt。
 * 可被 SPI 实现复用（如 Spring AI 实现）。
 *
 * @deprecated 使用 {@link com.ke.utopia.agent.memory.spi.defaults.DefaultPromptStrategy} 替代。
 */
@Deprecated
public final class PromptConstructor {

    private static final String SYSTEM_PROMPT_TEMPLATE =
            "You are an expert conversation analyst specializing in extracting\n" +
            "the TRUE INTENT behind multi-turn dialogues.\n\n" +
            "## Your Task\n" +
            "Analyze the following conversation and produce a structured summary\n" +
            "that captures what the user truly wants to accomplish.\n\n" +
            "## Analysis Framework\n" +
            "1. CORE INTENT: Distill the user's primary goal into one clear sentence.\n" +
            "   Go beyond surface-level requests to the underlying motivation.\n" +
            "2. KEY TOPICS: Identify 3-7 distinct themes or technical areas discussed.\n" +
            "   Use specific technical terms when applicable.\n" +
            "3. ACTION ITEMS: List any explicit requests, implied tasks, or decisions made.\n" +
            "   Distinguish between confirmed decisions and tentative suggestions.\n" +
            "4. EMOTIONAL TONE: Assess the user's sentiment.\n" +
            "   One of: neutral, frustrated, curious, satisfied, confused, urgent, enthusiastic.\n" +
            "5. DETAILED SUMMARY: Write a coherent 3-5 sentence narrative that:\n" +
            "   - Establishes context (who, what, why)\n" +
            "   - Traces the evolution of the discussion\n" +
            "   - Highlights key decisions and turning points\n" +
            "   - Notes any unresolved questions or open items\n\n" +
            "## Output Format\n" +
            "Respond ONLY with valid JSON (no markdown, no code fences):\n" +
            "{\n" +
            "  \"coreIntent\": \"<one sentence>\",\n" +
            "  \"keyTopics\": [\"<topic1>\", \"<topic2>\"],\n" +
            "  \"actionItems\": [\"<item1>\", \"<item2>\"],\n" +
            "  \"emotionalTone\": \"<one word>\",\n" +
            "  \"fullSummary\": \"<3-5 sentence narrative>\"\n" +
            "}\n\n" +
            "## Known User Profile\n%s\n\n" +
            "## Previous Summaries\n%s\n\n" +
            "## Conversation (%d messages)\n%s";

    public String constructPrompt(List<ConversationMessage> messages,
                           Optional<UserProfile> userProfile,
                           Optional<List<IntentSummary>> previousSummaries) {
        String profileSection = formatUserProfile(userProfile);
        String previousSection = formatPreviousSummaries(previousSummaries);
        String conversationSection = formatConversation(messages);

        return String.format(SYSTEM_PROMPT_TEMPLATE,
                profileSection, previousSection, messages.size(), conversationSection);
    }

    String formatConversation(List<ConversationMessage> messages) {
        return messages.stream()
                .map(m -> "[" + m.getRole() + "]: " + m.getContent())
                .collect(Collectors.joining("\n"));
    }

    String formatUserProfile(Optional<UserProfile> userProfile) {
        return userProfile.map(profile -> {
            StringBuilder sb = new StringBuilder();
            if (!profile.getProfileEntries().isEmpty()) {
                sb.append("User profile:\n");
                profile.getProfileEntries().forEach(e -> sb.append("- ").append(e.getContent()).append("\n"));
            }
            if (!profile.getMemoryEntries().isEmpty()) {
                sb.append("Agent notes:\n");
                profile.getMemoryEntries().forEach(e -> sb.append("- ").append(e.getContent()).append("\n"));
            }
            return sb.toString();
        }).orElse("No user profile available.");
    }

    String formatPreviousSummaries(Optional<List<IntentSummary>> previousSummaries) {
        return previousSummaries.map(summaries -> {
            if (summaries.isEmpty()) return "No previous summaries.";
            return summaries.stream()
                    .map(s -> "[Summary] Core intent: " + s.getCoreIntent() +
                            "\n  Topics: " + s.getKeyTopics() +
                            "\n  Actions: " + s.getActionItems())
                    .collect(Collectors.joining("\n\n"));
        }).orElse("No previous summaries.");
    }
}
