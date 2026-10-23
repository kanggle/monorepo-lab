package com.example.erp.approval.domain.request.repository;

import com.example.erp.approval.domain.request.ApprovalStatus;

import java.time.Instant;
import java.util.List;

/**
 * Outbound port for the overview performance metrics (TASK-MONO-809, ADR-MONO-084 P4).
 * Each method returns the <em>transition instants</em> of the matching requests, one per
 * request, over a half-open window {@code [from, to)} — the application service buckets
 * them into KST days. The unit is the request row, never an {@code approval_action} row
 * (an intermediate-stage approval writes an APPROVED action but does not finalize).
 *
 * <p>Every method carries {@code tenantId}; the {@code …ByParticipant} variants add the
 * list's participant predicate ({@code submitter_id = :p OR approver_id = :p}).
 */
public interface ApprovalMetricsQuery {

    /** {@code submitted_at} of requests submitted in the window, whatever their later status. */
    List<Instant> submittedAt(String tenantId, Instant from, Instant to);

    List<Instant> submittedAtByParticipant(String tenantId, String participantId,
                                           Instant from, Instant to);

    /**
     * {@code finalized_at} of requests whose status is {@code status}
     * ({@code APPROVED} or {@code REJECTED}) and that finalized in the window.
     */
    List<Instant> finalizedAt(String tenantId, ApprovalStatus status, Instant from, Instant to);

    List<Instant> finalizedAtByParticipant(String tenantId, String participantId,
                                           ApprovalStatus status, Instant from, Instant to);
}
