package com.ke.utopia.agent.memory.security;

import java.util.regex.Pattern;

/**
 * 威胁模式定义。
 */
public final class ThreatPattern {

    private final Pattern pattern;
    private final String threatId;
    private final ThreatCategory category;
    private final String description;

    public ThreatPattern(String regex, String threatId, ThreatCategory category, String description) {
        this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        this.threatId = threatId;
        this.category = category;
        this.description = description;
    }

    public Pattern getPattern() { return pattern; }
    public String getThreatId() { return threatId; }
    public ThreatCategory getCategory() { return category; }
    public String getDescription() { return description; }
}
