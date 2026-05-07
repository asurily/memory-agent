package com.ke.utopia.agent.memory.security;

import com.ke.utopia.agent.memory.model.SecurityScanResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * 安全扫描器。三层防护：
 * 1. 不可见 Unicode 字符检测
 * 2. 提示注入模式检测
 * 3. 数据泄露/后门植入检测
 */
public final class SecurityScanner {

    private static final Set<Character> INVISIBLE_CHARS = Set.of(
            '\u200b', '\u200c', '\u200d', '\u2060', '\ufeff',
            '\u202a', '\u202b', '\u202c', '\u202d', '\u202e'
    );

    private static final List<ThreatPattern> DEFAULT_THREAT_PATTERNS = Arrays.asList(
            // Prompt injection
            new ThreatPattern("ignore\\s+(previous|all|above|prior)\\s+instructions",
                    "prompt_injection", ThreatCategory.PROMPT_INJECTION, "Attempt to ignore instructions"),
            new ThreatPattern("you\\s+are\\s+now\\s+",
                    "role_hijack", ThreatCategory.ROLE_HIJACK, "Role hijack attempt"),
            new ThreatPattern("do\\s+not\\s+tell\\s+the\\s+user",
                    "deception_hide", ThreatCategory.PROMPT_INJECTION, "Deception instruction"),
            new ThreatPattern("system\\s+prompt\\s+override",
                    "sys_prompt_override", ThreatCategory.PROMPT_INJECTION, "System prompt override attempt"),
            new ThreatPattern("disregard\\s+(your|all|any)\\s+(instructions|rules)",
                    "disregard_rules", ThreatCategory.PROMPT_INJECTION, "Attempt to disregard rules"),
            new ThreatPattern("act\\s+as\\s+(if|though)\\s+you\\s+(have\\s+no)",
                    "bypass_restrictions", ThreatCategory.PROMPT_INJECTION, "Bypass restrictions attempt"),

            // Data exfiltration
            new ThreatPattern("curl\\s+[^\\n]*\\$\\{?\\w*(KEY|TOKEN|SECRET|PASSWORD)",
                    "exfil_curl", ThreatCategory.DATA_EXFILTRATION, "Credential exfiltration via curl"),
            new ThreatPattern("cat\\s+[^\\n]*(\\.env|credentials|\\.netrc)",
                    "read_secrets", ThreatCategory.DATA_EXFILTRATION, "Attempt to read secret files"),

            // Backdoor
            new ThreatPattern("authorized_keys",
                    "ssh_backdoor", ThreatCategory.BACKDOOR_IMPLANT, "SSH backdoor implant attempt")
    );

    private final List<ThreatPattern> threatPatterns;

    public SecurityScanner() {
        this.threatPatterns = new ArrayList<>(DEFAULT_THREAT_PATTERNS);
    }

    public SecurityScanner(List<ThreatPattern> customPatterns) {
        this.threatPatterns = new ArrayList<>(DEFAULT_THREAT_PATTERNS);
        this.threatPatterns.addAll(customPatterns);
    }

    /**
     * 扫描内容安全性。
     */
    public SecurityScanResult scan(String content) {
        if (content == null || content.isEmpty()) {
            return SecurityScanResult.safe();
        }

        // Phase 1: Invisible Unicode detection
        for (char c : content.toCharArray()) {
            if (INVISIBLE_CHARS.contains(c)) {
                return SecurityScanResult.blocked(
                        "invisible_unicode",
                        "Content contains invisible unicode character U+" + String.format("%04X", (int) c));
            }
        }

        // Phase 2 & 3: Threat pattern detection
        for (ThreatPattern tp : threatPatterns) {
            if (tp.getPattern().matcher(content).find()) {
                return SecurityScanResult.blocked(tp.getThreatId(), tp.getDescription());
            }
        }

        return SecurityScanResult.safe();
    }

    /**
     * 扫描并抛出异常（如果内容不安全）。
     */
    public void scanAndThrow(String content) {
        SecurityScanResult result = scan(content);
        if (!result.isSafe()) {
            throw new com.ke.utopia.agent.memory.exception.SecurityBlockedException(
                    result.getThreatType(), result.getThreatDescription());
        }
    }
}
