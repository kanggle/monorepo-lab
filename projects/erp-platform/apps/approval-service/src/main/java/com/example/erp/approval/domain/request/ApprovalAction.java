package com.example.erp.approval.domain.request;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * One accepted transition of an approval request (architecture.md § Approval
 * Request aggregate lifecycle). Drives the response {@code history} array
 * (one immutable entry per transition, E4). {@code reason} is required on
 * reject + withdraw. Append-only — there are no mutators.
 *
 * <p>JPA annotations are the allowed domain↔framework exception.
 */
@Entity
@Table(name = "approval_action")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApprovalAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "approval_request_id", length = 48, nullable = false)
    private String approvalRequestId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "transition", length = 16, nullable = false)
    private ApprovalStatus transition;

    @Column(name = "actor", length = 128, nullable = false)
    private String actor;

    @Column(name = "reason", length = 512)
    private String reason;

    /**
     * The 0-based route stage this transition pertains to (TASK-ERP-BE-012).
     * Nullable for pre-v2 rows; new transitions always populate it. For a
     * submit/withdraw it is the current position (0 on submit); for an
     * approve/reject it is the stage that acted.
     */
    @Column(name = "stage")
    private Integer stage;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public static ApprovalAction of(String tenantId, String approvalRequestId,
                                    ApprovalStatus transition, String actor,
                                    String reason, Integer stage, Instant occurredAt) {
        ApprovalAction a = new ApprovalAction();
        a.tenantId = tenantId;
        a.approvalRequestId = approvalRequestId;
        a.transition = transition;
        a.actor = actor;
        a.reason = reason;
        a.stage = stage;
        a.occurredAt = occurredAt;
        return a;
    }
}
