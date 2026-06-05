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
 * lifecycle + § v2.0 amendment). Carries {@code (id, tenant_id, subject,
 * multi-stage route position, status, submitter, version)}. State changes happen
 * ONLY through the {@link ApprovalStateMachine}; the transition methods funnel
 * through it so the persistence adapter never observes an illegal intermediate
 * state (T4 — no direct status UPDATE).
 *
 * <p>JPA annotations are the single allowed domain↔framework exception
 * (architecture.md § Boundary rules); the invariant logic is otherwise pure.
 * {@code @Version} gives optimistic locking (transactional T5).
 *
 * <p>v2.0 (TASK-ERP-BE-012) — the route is an ordered 1~N stage list persisted in
 * {@code approval_route_stage}; the aggregate tracks its position with
 * {@code currentStageIndex} (0-based) + {@code totalStages}. The application
 * service loads the resolved {@link ApprovalRoute} and passes it into
 * {@link #approve}/{@link #reject} so the aggregate can authorize the CURRENT
 * stage's approver and decide last-vs-intermediate. {@code approverId} is
 * denormalized = the current stage's approver (read back-compat; it follows the
 * advancing stage). Self-approval / duplicate approvers are structurally
 * forbidden at route construction (see {@link ApprovalRoute}).
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

    @Column(name = "current_stage_index", nullable = false)
    private int currentStageIndex;

    @Column(name = "total_stages", nullable = false)
    private int totalStages;

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
     * Create an approval request in initial state DRAFT with a 1~N stage route.
     * The route (no self-approval / no duplicate / non-blank stages) is validated
     * by {@link ApprovalRoute} BEFORE this factory is reached. {@code approverId}
     * is denormalized = stage 0's approver; {@code totalStages} = route length;
     * {@code currentStageIndex} = 0.
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
        r.approverId = route.approverAt(0).approverId();
        r.submitterId = submitterId;
        r.status = ApprovalStatus.DRAFT;
        r.currentStageIndex = 0;
        r.totalStages = route.stageCount();
        r.createdAt = now;
        r.updatedAt = now;
        return r;
    }

    public ApprovalSubject subject() {
        return new ApprovalSubject(subjectType, subjectId);
    }

    public boolean isFinalized() {
        return status.isFinalized();
    }

    // -----------------------------------------------------------------------
    // Transitions — each funnels through ApprovalStateMachine.next(...) and
    // applies the per-stage approver/submitter/reason guards in the
    // architecture.md order.
    // -----------------------------------------------------------------------

    /**
     * {@code DRAFT → SUBMITTED}. The caller (submit use case) has already run the
     * masterdata subject reference-integrity check (E1) + route validity; this
     * method advances the state machine, stamps {@code submittedAt} and resets the
     * route position to stage 0.
     */
    public void submit(Instant now) {
        // submit is stage-independent (isLastStage ignored).
        this.status = ApprovalStateMachine.next(this.status, ApprovalCommand.SUBMIT, true);
        this.currentStageIndex = 0;
        this.submittedAt = now;
        this.updatedAt = now;
    }

    /**
     * {@code SUBMITTED|IN_REVIEW → APPROVED (final stage) | IN_REVIEW (advance)}.
     * Only the CURRENT stage's approver may approve (E3 / I4 — sequential order
     * enforced); authorization is checked AFTER the legal-edge / finalized guards
     * (so an illegal-edge / finalized request reports those, not authz). A
     * non-final approval advances {@code currentStageIndex}, re-points the
     * denormalized {@code approverId} to the next stage and leaves
     * {@code finalizedAt} unset; the final approval stamps {@code finalizedAt}.
     */
    public void approve(String actorId, ApprovalRoute route, Instant now) {
        boolean isLastStage = route.isLastStage(this.currentStageIndex);
        ApprovalStatus to = ApprovalStateMachine.next(this.status, ApprovalCommand.APPROVE, isLastStage);
        ensureActingStageApprover(actorId, route);
        if (to == ApprovalStatus.IN_REVIEW) {
            this.currentStageIndex++;
            this.approverId = route.approverAt(this.currentStageIndex).approverId();
            this.status = ApprovalStatus.IN_REVIEW;
            this.updatedAt = now;
        } else {
            this.status = ApprovalStatus.APPROVED;
            this.finalizedAt = now;
            this.updatedAt = now;
        }
    }

    /**
     * {@code SUBMITTED|IN_REVIEW → REJECTED}. Only the current stage's approver
     * may reject; {@code reason} is required (E4 — 반려 시 사유 필수). Reject from any
     * stage finalizes the request.
     */
    public void reject(String actorId, ApprovalRoute route, String reason, Instant now) {
        // reject is stage-independent for the next-state (isLastStage ignored).
        ApprovalStatus to = ApprovalStateMachine.next(this.status, ApprovalCommand.REJECT, true);
        ensureActingStageApprover(actorId, route);
        ensureReasonPresent(reason, "reject");
        this.status = to;
        this.finalizedAt = now;
        this.updatedAt = now;
    }

    /**
     * {@code DRAFT|SUBMITTED|IN_REVIEW → WITHDRAWN}. Only the submitter may
     * withdraw their own request; {@code reason} is required (E4).
     */
    public void withdraw(String actorId, String reason, Instant now) {
        ApprovalStatus to = ApprovalStateMachine.next(this.status, ApprovalCommand.WITHDRAW, true);
        ensureActingSubmitter(actorId);
        ensureReasonPresent(reason, "withdraw");
        this.status = to;
        this.finalizedAt = now;
        this.updatedAt = now;
    }

    /** True iff this request is mid-route (advanced past stage 0, not finalized). */
    public boolean isInReview() {
        return status == ApprovalStatus.IN_REVIEW;
    }

    private void ensureActingStageApprover(String actorId, ApprovalRoute route) {
        if (!route.isApproverAt(this.currentStageIndex, actorId)) {
            throw new ApprovalNotAuthorizedApproverException(
                    "principal '" + actorId + "' is not the current stage ("
                            + this.currentStageIndex + ") approver '"
                            + route.approverAt(this.currentStageIndex).approverId() + "'");
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
