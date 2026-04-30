package com.example.security.domain.suspicious;

import com.example.security.domain.detection.RiskLevel;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Aggregate root for a persisted suspicious event. Immutable once created;
 * action result is appended via {@link #withLockRequestResult(String)} returning
 * a new instance.
 */
public final class SuspiciousEvent {

    private final String id;
    private final String accountId;
    private final String ruleCode;
    private final int riskScore;
    private final RiskLevel actionTaken;
    private final Map<String, Object> evidence;
    private final String triggerEventId;
    private final Instant detectedAt;
    private final String lockRequestResult; // null | SUCCESS | FAILURE | ALREADY_LOCKED | PENDING

    private SuspiciousEvent(String id, String accountId, String ruleCode, int riskScore,
                            RiskLevel actionTaken, Map<String, Object> evidence,
                            String triggerEventId, Instant detectedAt, String lockRequestResult) {
        this.id = Objects.requireNonNull(id);
        this.accountId = Objects.requireNonNull(accountId);
        this.ruleCode = Objects.requireNonNull(ruleCode);
        this.riskScore = riskScore;
        this.actionTaken = Objects.requireNonNull(actionTaken);
        this.evidence = evidence == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(evidence));
        this.triggerEventId = triggerEventId;
        this.detectedAt = Objects.requireNonNull(detectedAt);
        this.lockRequestResult = lockRequestResult;
    }

    public static SuspiciousEvent create(String id, String accountId, String ruleCode,
                                         int riskScore, RiskLevel actionTaken,
                                         Map<String, Object> evidence,
                                         String triggerEventId, Instant detectedAt) {
        return new SuspiciousEvent(id, accountId, ruleCode, riskScore, actionTaken,
                evidence, triggerEventId, detectedAt, null);
    }

    public SuspiciousEvent withLockRequestResult(String result) {
        return new SuspiciousEvent(id, accountId, ruleCode, riskScore, actionTaken,
                evidence, triggerEventId, detectedAt, result);
    }

    public String getId() { return id; }
    public String getAccountId() { return accountId; }
    public String getRuleCode() { return ruleCode; }
    public int getRiskScore() { return riskScore; }
    public RiskLevel getActionTaken() { return actionTaken; }
    public Map<String, Object> getEvidence() { return evidence; }
    public String getTriggerEventId() { return triggerEventId; }
    public Instant getDetectedAt() { return detectedAt; }
    public String getLockRequestResult() { return lockRequestResult; }
}
