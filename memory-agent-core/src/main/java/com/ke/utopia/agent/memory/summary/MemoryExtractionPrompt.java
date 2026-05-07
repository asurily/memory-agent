package com.ke.utopia.agent.memory.summary;

import com.ke.utopia.agent.memory.model.ConversationMessage;
import com.ke.utopia.agent.memory.model.MemoryExtraction;
import com.ke.utopia.agent.memory.model.UserProfile;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 记忆提取的 Prompt 模板构造器。
 *
 * @deprecated 使用 {@link com.ke.utopia.agent.memory.spi.defaults.DefaultPromptStrategy} 替代。
 */
@Deprecated
public final class MemoryExtractionPrompt {

    private static final String EXTRACTION_SYSTEM_PROMPT =
            "You are a memory extraction specialist. Analyze the conversation and extract " +
            "factual information that should be remembered about the user or the conversation.\n\n" +
            "## Rules\n" +
            "1. Only extract information that is explicitly stated or strongly implied.\n" +
            "2. Each extraction should be a concise, self-contained fact.\n" +
            "3. Assign a confidence score (0.0-1.0) indicating how certain the extraction is.\n" +
            "4. Assign a category: \"preference\", \"fact\", \"instruction\", \"identity\", \"relationship\", \"general\".\n" +
            "5. Classify as MEMORY (agent notes) or USER_PROFILE (user personal info/preferences).\n" +
            "6. Do NOT extract information that is already in the existing profile below.\n" +
            "7. Assign an importanceScore (0.0-1.0) indicating how important this memory is:\n" +
            "   - 0.9-1.0: Critical identity information (name, role, key constraints)\n" +
            "   - 0.6-0.8: Important preferences, habits, or long-term goals\n" +
            "   - 0.3-0.5: General facts and minor details\n" +
            "   - 0.0-0.2: Transient or temporary information\n\n" +
            "## Output Format\n" +
            "Respond ONLY with valid JSON array (no markdown, no code fences):\n" +
            "[\n" +
            "  {\"content\":\"...\",\"type\":\"MEMORY\",\"confidence\":0.9,\"category\":\"preference\",\"importanceScore\":0.8}\n" +
            "]\n\n" +
            "If no memorable information is found, return an empty array: []\n\n" +
            "## Existing User Profile\n%s\n\n" +
            "## Conversation\n%s";

    public String constructExtractionPrompt(List<ConversationMessage> messages,
                                             Optional<UserProfile> existingProfile) {
        String profileSection = formatProfile(existingProfile);
        String conversationSection = formatConversation(messages);
        return String.format(EXTRACTION_SYSTEM_PROMPT, profileSection, conversationSection);
    }

    private String formatProfile(Optional<UserProfile> profile) {
        return profile.map(p -> {
            StringBuilder sb = new StringBuilder();
            if (!p.getProfileEntries().isEmpty()) {
                sb.append("User profile entries:\n");
                p.getProfileEntries().forEach(e -> sb.append("- ").append(e.getContent()).append("\n"));
            }
            if (!p.getMemoryEntries().isEmpty()) {
                sb.append("Agent memory entries:\n");
                p.getMemoryEntries().forEach(e -> sb.append("- ").append(e.getContent()).append("\n"));
            }
            if (sb.length() == 0) {
                sb.append("No existing profile.");
            }
            return sb.toString();
        }).orElse("No existing profile.");
    }

    private String formatConversation(List<ConversationMessage> messages) {
        return messages.stream()
                .map(m -> "[" + m.getRole() + "]: " + m.getContent())
                .collect(Collectors.joining("\n"));
    }
}
