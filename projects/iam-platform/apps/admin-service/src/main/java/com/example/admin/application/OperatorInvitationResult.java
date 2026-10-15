package com.example.admin.application;

import java.time.Instant;
import java.util.List;

/**
 * TASK-MONO-772 S2 — one invitation as the management surface shows it (admin-api.md «공통 응답 항목»).
 *
 * <p>🔴 Deliberately has no token, no token hash, no link and no accepting account id — the response type cannot
 * leak what it does not carry. {@code expired} is the judgement at read time ({@code PENDING} and
 * {@code expiresAt ≤ now}); {@code auditId} is set only on the create response. {@code firstAdmin} (TASK-MONO-773
 * S4) marks a first-admin invitation.
 */
public record OperatorInvitationResult(
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
        String deliveryStatus,
        Instant deliveryAttemptedAt,
        Instant acceptedAt,
        String acceptedOperatorId,
        Instant cancelledAt,
        String auditId,
        boolean firstAdmin
) {
    /** The 772 shape — an ordinary invitation. */
    public OperatorInvitationResult(String invitationId, String tenantId, String email, String displayName,
                                    List<String> roles, String status, boolean expired, Instant expiresAt,
                                    Instant createdAt, String invitedBy, String deliveryStatus,
                                    Instant deliveryAttemptedAt, Instant acceptedAt, String acceptedOperatorId,
                                    Instant cancelledAt, String auditId) {
        this(invitationId, tenantId, email, displayName, roles, status, expired, expiresAt, createdAt, invitedBy,
                deliveryStatus, deliveryAttemptedAt, acceptedAt, acceptedOperatorId, cancelledAt, auditId, false);
    }

    /** {@code PENDING} and the expiry has passed (or is now). {@code ACCEPTED}/{@code CANCELLED} never expire. */
    public static boolean isExpired(String status, Instant expiresAt, Instant now) {
        return "PENDING".equals(status) && expiresAt != null && !expiresAt.isAfter(now);
    }

    public OperatorInvitationResult withAuditId(String newAuditId) {
        return new OperatorInvitationResult(invitationId, tenantId, email, displayName, roles, status, expired,
                expiresAt, createdAt, invitedBy, deliveryStatus, deliveryAttemptedAt, acceptedAt,
                acceptedOperatorId, cancelledAt, newAuditId, firstAdmin);
    }
}
