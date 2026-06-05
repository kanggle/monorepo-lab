package com.example.erp.readmodel.domain.approval;

import java.time.Instant;
import java.util.Objects;

/**
 * Projection of the <b>latest fact</b> of an approval request (read-only, E5).
 * Maintained by the {@code erp.approval.*.v1} consumers as a single-row upsert
 * keyed by {@code approvalRequestId} (= aggregateId). It holds the current
 * status + route ids + timestamps + last reason — <b>NOT</b> the authoritative
 * transition {@code history} (which stays with {@code approval-service};
 * {@code GET /api/erp/approval/requests/{id}} is the source of record). Pure
 * Java — no framework annotations (Hexagonal domain).
 *
 * <p><b>Terminal-once (E3).</b> Once the projection reaches a terminal status
 * ({@code APPROVED}/{@code REJECTED}/{@code WITHDRAWN}), a later non-duplicate
 * transition never reverts it to {@code SUBMITTED}: {@link #applySubmitted} is a
 * no-op on a terminal row, and a later terminal is last-terminal-wins (the
 * terminal stays terminal). The producer's per-{@code approvalRequestId}
 * partition ordering means {@code submitted} normally precedes its terminal;
 * the guard only protects against replay / out-of-contract delivery.
 *
 * <p><b>Out-of-order tolerance.</b> A terminal arriving with no prior
 * {@code submitted} (compaction / replay-from-middle) still produces a row via
 * {@link #ofTerminal}; {@code submittedAt} is left {@code null} (ABSENT —
 * never fabricated, E5).
 */
public final class ApprovalFactProjection {

    private final String approvalRequestId;
    private ApprovalStatus status;
    private ApprovalSubjectType subjectType;
    private String subjectId;
    private String approverId;
    private String submitterId;
    private Instant submittedAt;
    private Instant finalizedAt;
    private String lastReason;
    private Instant lastEventAt;
    private String lastEventId;

    public ApprovalFactProjection(String approvalRequestId, ApprovalStatus status,
                                  ApprovalSubjectType subjectType, String subjectId,
                                  String approverId, String submitterId,
                                  Instant submittedAt, Instant finalizedAt, String lastReason,
                                  Instant lastEventAt, String lastEventId) {
        this.approvalRequestId = Objects.requireNonNull(approvalRequestId, "approvalRequestId");
        this.status = status;
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.approverId = approverId;
        this.submitterId = submitterId;
        this.submittedAt = submittedAt;
        this.finalizedAt = finalizedAt;
        this.lastReason = lastReason;
        this.lastEventAt = lastEventAt;
        this.lastEventId = lastEventId;
    }

    /**
     * Factory for a brand-new {@code SUBMITTED} fact (the request's first
     * projected event in normal ordering).
     */
    public static ApprovalFactProjection ofSubmitted(String approvalRequestId,
                                                     ApprovalSubjectType subjectType,
                                                     String subjectId, String approverId,
                                                     String submitterId, Instant submittedAt,
                                                     Instant lastEventAt, String lastEventId) {
        return new ApprovalFactProjection(approvalRequestId, ApprovalStatus.SUBMITTED,
                subjectType, subjectId, approverId, submitterId,
                submittedAt, null, null, lastEventAt, lastEventId);
    }

    /**
     * Factory for a terminal fact produced when the terminal event arrives with
     * no prior {@code submitted} row (out-of-order). {@code submittedAt} is left
     * {@code null} (ABSENT — no fabrication, E5).
     */
    public static ApprovalFactProjection ofTerminal(String approvalRequestId,
                                                    ApprovalStatus terminalStatus,
                                                    ApprovalSubjectType subjectType,
                                                    String subjectId, String approverId,
                                                    String submitterId, Instant finalizedAt,
                                                    String reason, Instant lastEventAt,
                                                    String lastEventId) {
        return new ApprovalFactProjection(approvalRequestId, terminalStatus,
                subjectType, subjectId, approverId, submitterId,
                null, finalizedAt, reason, lastEventAt, lastEventId);
    }

    /**
     * Applies a {@code submitted} transition to an EXISTING row. Terminal-once:
     * a no-op (status / finalizedAt / lastReason) when the row is already
     * terminal — never reverts a terminal to {@code SUBMITTED}. On a
     * non-terminal row it (re)stamps {@code SUBMITTED} + {@code submittedAt} +
     * the route ids (idempotent latest-wins). Always advances the provenance
     * timestamps for a non-duplicate event.
     */
    public void applySubmitted(ApprovalSubjectType subjectType, String subjectId,
                               String approverId, String submitterId, Instant submittedAt,
                               Instant lastEventAt, String lastEventId) {
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.approverId = approverId;
        this.submitterId = submitterId;
        if (this.submittedAt == null) {
            this.submittedAt = submittedAt;
        }
        if (!isTerminal()) {
            this.status = ApprovalStatus.SUBMITTED;
        }
        this.lastEventAt = lastEventAt;
        this.lastEventId = lastEventId;
    }

    /**
     * Applies a terminal transition to an EXISTING row. Last-terminal-wins: it
     * sets the terminal {@code status} + {@code finalizedAt} + {@code reason}
     * (when supplied) and refreshes the route ids — but a terminal never reverts
     * to {@code SUBMITTED}. {@code submittedAt} is preserved (not cleared).
     */
    public void applyTerminal(ApprovalStatus terminalStatus, ApprovalSubjectType subjectType,
                              String subjectId, String approverId, String submitterId,
                              Instant finalizedAt, String reason,
                              Instant lastEventAt, String lastEventId) {
        this.status = terminalStatus;
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.approverId = approverId;
        this.submitterId = submitterId;
        if (finalizedAt != null) {
            this.finalizedAt = finalizedAt;
        }
        if (reason != null) {
            this.lastReason = reason;
        }
        this.lastEventAt = lastEventAt;
        this.lastEventId = lastEventId;
    }

    public boolean isTerminal() {
        return status != null && status.isTerminal();
    }

    public String approvalRequestId() { return approvalRequestId; }
    public ApprovalStatus status() { return status; }
    public ApprovalSubjectType subjectType() { return subjectType; }
    public String subjectId() { return subjectId; }
    public String approverId() { return approverId; }
    public String submitterId() { return submitterId; }
    public Instant submittedAt() { return submittedAt; }
    public Instant finalizedAt() { return finalizedAt; }
    public String lastReason() { return lastReason; }
    public Instant lastEventAt() { return lastEventAt; }
    public String lastEventId() { return lastEventId; }
}
