package com.example.erp.approval.domain.request;

import com.example.erp.approval.domain.error.ApprovalErrors.ApprovalNotAuthorizedApproverException;
import com.example.erp.approval.domain.error.ApprovalErrors.ValidationException;
import com.example.erp.approval.domain.route.ApprovalRoute;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;

/**
 * Approval request aggregate root (architecture.md § Approval Request aggregate
 * lifecycle). Carries {@code (id, tenant_id, subject, single-stage route,
 * status, submitter, version)}. State changes happen ONLY through the
 * {@link ApprovalStateMachine}; the transition methods funnel through it so the
 * persistence adapter never observes an illegal intermediate state (T4 — no
 * direct status UPDATE).
 *
 * <p>JPA annotations are the single allowed domain↔framework exception
 * (architecture.md § Boundary rules); the invariant logic is otherwise pure.
 * {@code @Version} gives optimistic locking (transactional T5).
 *
 * <p>The single-stage route is denormalized onto the aggregate as
 * {@code approverId}; {@code submitterId} is the create-time actor. Self-approval
 * is structurally forbidden at construction (see {@link ApprovalRoute}).
 */
@Entity
@Table(name = "approval_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApprovalRequest {

    @Id
    @Column(name = "id", length = 48, nullable = false)
    private String id;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "subject_type", length = 16, nullable = false)
    private SubjectType subjectType;

    @Column(name = "subject_id", length = 64, nullable = false)
    private String subjectId;

    @Column(name = "title", length = 256, nullable = false)
    private String title;

    @Column(name = "creation_reason", length = 512)
    private String creationReason;

    @Column(name = "approver_id", length = 64, nullable = false)
    private String approverId;

    @Column(name = "submitter_id", length = 64, nullable = false)
    private String submitterId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", length = 16, nullable = false)
    private ApprovalStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * Create an approval request in initial state DRAFT. The route
     * (submitter ≠ approver) is validated by {@link ApprovalRoute#singleStage}
     * BEFORE this factory is reached — a self-approving route never produces a
     * request.
     */
    public static ApprovalRequest createDraft(String id, String tenantId,
                                              ApprovalSubject subject, String title,
                                              String creationReason,
                                              ApprovalRoute route, String submitterId,
                                              Instant now) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(submitterId, "submitterId");
        Objects.requireNonNull(now, "now");
        ApprovalRequest r = new ApprovalRequest();
        r.id = id;
        r.tenantId = tenantId;
        r.subjectType = subject.subjectType();
        r.subjectId = subject.subjectId();
        r.title = title;
        r.creationReason = creationReason;
        r.approverId = route.approverId();
        r.submitterId = submitterId;
        r.status = ApprovalStatus.DRAFT;
        r.createdAt = now;
        r.updatedAt = now;
        return r;
    }

    public ApprovalSubject subject() {
        return new ApprovalSubject(subjectType, subjectId);
    }

    public ApprovalRoute route() {
        return new ApprovalRoute(new com.example.erp.approval.domain.route.Approver(approverId));
    }

    public boolean isFinalized() {
        return status.isFinalized();
    }

    // -----------------------------------------------------------------------
    // Transitions — each funnels through ApprovalStateMachine.next(...) and
    // applies the route/approver/reason guards in the architecture.md order.
    // -----------------------------------------------------------------------

    /**
     * {@code DRAFT → SUBMITTED}. The caller (submit use case) has already run
     * the masterdata subject reference-integrity check (E1) + route validity;
     * this method advances the state machine and stamps {@code submittedAt}.
     */
    public void submit(Instant now) {
        this.status = ApprovalStateMachine.next(this.status, ApprovalCommand.SUBMIT);
        this.submittedAt = now;
        this.updatedAt = now;
    }

    /**
     * {@code SUBMITTED → APPROVED}. Only the route's approver may approve
     * (E3 / I4) — checked AFTER the legal-edge / finalized guards (so an
     * illegal-edge / finalized request reports those, not authz).
     */
    public void approve(String actorId, Instant now) {
        ApprovalStatus to = ApprovalStateMachine.next(this.status, ApprovalCommand.APPROVE);
        ensureActingApprover(actorId);
        this.status = to;
        this.finalizedAt = now;
        this.updatedAt = now;
    }

    /**
     * {@code SUBMITTED → REJECTED}. Only the route's approver may reject;
     * {@code reason} is required (E4 — 반려 시 사유 필수).
     */
    public void reject(String actorId, String reason, Instant now) {
        ApprovalStatus to = ApprovalStateMachine.next(this.status, ApprovalCommand.REJECT);
        ensureActingApprover(actorId);
        ensureReasonPresent(reason, "reject");
        this.status = to;
        this.finalizedAt = now;
        this.updatedAt = now;
    }

    /**
     * {@code DRAFT|SUBMITTED → WITHDRAWN}. Only the submitter may withdraw their
     * own request; {@code reason} is required (E4).
     */
    public void withdraw(String actorId, String reason, Instant now) {
        ApprovalStatus to = ApprovalStateMachine.next(this.status, ApprovalCommand.WITHDRAW);
        ensureActingSubmitter(actorId);
        ensureReasonPresent(reason, "withdraw");
        this.status = to;
        this.finalizedAt = now;
        this.updatedAt = now;
    }

    private void ensureActingApprover(String actorId) {
        if (!Objects.equals(this.approverId, actorId)) {
            throw new ApprovalNotAuthorizedApproverException(
                    "principal '" + actorId + "' is not the route's approver '"
                            + this.approverId + "'");
        }
    }

    private void ensureActingSubmitter(String actorId) {
        if (!Objects.equals(this.submitterId, actorId)) {
            throw new ApprovalNotAuthorizedApproverException(
                    "principal '" + actorId + "' is not the submitter '"
                            + this.submitterId + "' — only the submitter may withdraw");
        }
    }

    private static void ensureReasonPresent(String reason, String action) {
        if (reason == null || reason.isBlank()) {
            throw new ValidationException("reason is required for " + action);
        }
    }
}
