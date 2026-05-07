package com.ke.utopia.agent.memory.model;

/**
 * 安全扫描结果。
 */
public final class SecurityScanResult {

    private final boolean safe;
    private final String threatType;
    private final String threatDescription;

    private SecurityScanResult(boolean safe, String threatType, String threatDescription) {
        this.safe = safe;
        this.threatType = threatType;
        this.threatDescription = threatDescription;
    }

    public static SecurityScanResult safe() {
        return new SecurityScanResult(true, null, null);
    }

    public static SecurityScanResult blocked(String threatType, String description) {
        return new SecurityScanResult(false, threatType, description);
    }

    public boolean isSafe() { return safe; }
    public String getThreatType() { return threatType; }
    public String getThreatDescription() { return threatDescription; }

    @Override
    public String toString() {
        return safe ? "SecurityScanResult{SAFE}" :
                "SecurityScanResult{BLOCKED: " + threatType + " - " + threatDescription + "}";
    }
}
