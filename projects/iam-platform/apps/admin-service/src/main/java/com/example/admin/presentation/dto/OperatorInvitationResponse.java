package com.example.admin.presentation.dto;

import com.example.admin.application.OperatorInvitationResult;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * TASK-MONO-772 S2 — the invitation item (admin-api.md «공통 응답 항목»), shared by create · list · cancel · resend.
 *
 * <p>Every key is always present — {@code null} when not applicable ({@code acceptedAt} on a pending row,
 * {@code delivery} before the first attempt). {@code ALWAYS} is explicit so the wire shape does not depend on the
 * ObjectMapper's global inclusion: the {@code test} profile sets {@code non_null}, which dropped null keys from
 * the TASK-MONO-771 S5 response in its first CI run. {@code auditId} is the one exception — it exists only on the
 * create response (the contract's «invitation item + auditId»), so it is omitted when null.
 *
 * <p>🔴 There is no token, token hash, link or accepting-account field — this type cannot carry them.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record OperatorInvitationResponse(
        String invitationId,
        String tenantId,
        String email,
        String displayName,
        List<String> roles,
        String status,
        boolean expired,
        Instant expiresAt,
        Instant createdAt,
        String invitedBy,
        Delivery delivery,
        Instant acceptedAt,
        String acceptedOperatorId,
        Instant cancelledAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) String auditId
) {

    /** {@code delivery.status} ∈ {@code SENT · FAILED_TRANSIENT · FAILED_PERMANENT}; the whole object is null before any attempt. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Delivery(String status, Instant attemptedAt) {}

    public static OperatorInvitationResponse from(OperatorInvitationResult r) {
        Delivery delivery = r.deliveryStatus() == null ? null : new Delivery(r.deliveryStatus(), r.deliveryAttemptedAt());
        return new OperatorInvitationResponse(
                r.invitationId(), r.tenantId(), r.email(), r.displayName(), r.roles(), r.status(), r.expired(),
                r.expiresAt(), r.createdAt(), r.invitedBy(), delivery,
                r.acceptedAt(), r.acceptedOperatorId(), r.cancelledAt(), r.auditId());
    }
}
