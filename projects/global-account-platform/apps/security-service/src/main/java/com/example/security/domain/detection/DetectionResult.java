package com.example.security.domain.detection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Result of a {@link SuspiciousActivityRule} evaluation.
 *
 * @param ruleCode   rule identifier (VELOCITY, GEO_ANOMALY, DEVICE_CHANGE, TOKEN_REUSE)
 * @param riskScore  0..100, where 0 means "rule did not fire"
 * @param evidence   rule-specific context (no PII); stored into suspicious_events.evidence
 */
public record DetectionResult(String ruleCode, int riskScore, Map<String, Object> evidence) {

    public static final DetectionResult NONE = new DetectionResult("NONE", 0, Collections.emptyMap());

    public DetectionResult {
        if (ruleCode == null || ruleCode.isBlank()) {
            throw new IllegalArgumentException("ruleCode must not be blank");
        }
        if (riskScore < 0 || riskScore > 100) {
            throw new IllegalArgumentException("riskScore must be in [0, 100], got " + riskScore);
        }
        evidence = evidence == null ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(evidence));
    }

    public boolean fired() {
        return riskScore > 0;
    }
}
