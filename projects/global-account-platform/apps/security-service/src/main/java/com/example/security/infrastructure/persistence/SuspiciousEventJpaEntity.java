package com.example.security.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "suspicious_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SuspiciousEventJpaEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    // TASK-BE-248: leads (tenant_id, account_id, detected_at) on
    // idx_suspicious_tenant_account_detected (V0008 migration).
    @Column(name = "tenant_id", length = 32, nullable = false)
    private String tenantId;

    @Column(name = "account_id", length = 36, nullable = false)
    private String accountId;

    @Column(name = "rule_code", length = 30, nullable = false)
    private String ruleCode;

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    @Column(name = "action_taken", length = 20, nullable = false)
    private String actionTaken;

    @Column(name = "evidence", columnDefinition = "JSON")
    private String evidence;

    @Column(name = "trigger_event_id", length = 36)
    private String triggerEventId;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "lock_request_result", length = 30)
    private String lockRequestResult;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    public static SuspiciousEventJpaEntity create(String id, String tenantId, String accountId, String ruleCode,
                                                   int riskScore, String actionTaken,
                                                   String evidenceJson, String triggerEventId,
                                                   Instant detectedAt, String lockRequestResult) {
        SuspiciousEventJpaEntity e = new SuspiciousEventJpaEntity();
        e.id = id;
        e.tenantId = tenantId;
        e.accountId = accountId;
        e.ruleCode = ruleCode;
        e.riskScore = riskScore;
        e.actionTaken = actionTaken;
        e.evidence = evidenceJson;
        e.triggerEventId = triggerEventId;
        e.detectedAt = detectedAt;
        e.lockRequestResult = lockRequestResult;
        return e;
    }

    void updateLockRequestResult(String result) {
        this.lockRequestResult = result;
    }
}
