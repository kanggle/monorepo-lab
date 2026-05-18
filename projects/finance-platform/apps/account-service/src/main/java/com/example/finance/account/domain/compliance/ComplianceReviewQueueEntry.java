package com.example.finance.account.domain.compliance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Operator review-queue row (fintech F4/F8). A sanction hit (F4) or a
 * reconciliation discrepancy (F8) enters this queue and is NEVER auto-cleared
 * — only an operator (v2 admin-service) resolves it. v1 only ever inserts
 * {@code OPEN} rows; there is no auto-resolve path.
 *
 * <p>No regulated PII is stored here beyond the non-PII screening ref + the
 * account/txn ids (F7 — matched-list detail is out of scope for v1, would be
 * encrypted if added).
 */
@Entity
@Table(name = "compliance_review_queue")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceReviewQueueEntry {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "account_id", length = 36, nullable = false)
    private String accountId;

    @Column(name = "transaction_id", length = 36)
    private String transactionId;

    @Column(name = "review_type", length = 40, nullable = false)
    private String reviewType;

    @Column(name = "screening_ref", length = 128)
    private String screeningRef;

    @Column(name = "status", length = 20, nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static ComplianceReviewQueueEntry sanctionHit(String id,
                                                         String tenantId,
                                                         String accountId,
                                                         String transactionId,
                                                         String screeningRef,
                                                         Instant now) {
        ComplianceReviewQueueEntry e = new ComplianceReviewQueueEntry();
        e.id = id;
        e.tenantId = tenantId;
        e.accountId = accountId;
        e.transactionId = transactionId;
        e.reviewType = "SANCTION_HIT";
        e.screeningRef = screeningRef;
        e.status = "OPEN";
        e.createdAt = now;
        return e;
    }
}
