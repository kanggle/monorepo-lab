package com.example.admin.application;

import java.time.Instant;

/**
 * TASK-BE-634 — every audit-row {@code idempotency_key} that admin-service builds itself (as opposed to one a caller
 * sends in an {@code Idempotency-Key} header) is built here, so one test ({@code AuditIdempotencyKeysTest}) can hold
 * all of them to the column: {@code admin_actions.idempotency_key VARCHAR(100)}.
 *
 * <p>Why it matters: the audit write is fail-closed, so a key one character too long does not lose an audit row —
 * it aborts the whole command as {@code 500 AUDIT_FAILURE}. Unit tests mock the auditor and never see the column, so
 * the overflow only shows against real MySQL ({@code TASK-BE-631} found the assignment key that way; this ticket
 * found the participant key, which was 127/130 characters and failed on every call).
 *
 * <p>The action name is not part of any key: the row carries it in {@code action_code} and the unique index is
 * {@code (actor_id, action_code, idempotency_key)}. None of these keys is read back for a retry decision — each
 * ends in the instant, so it only separates rows.
 *
 * <p>Maxima use the widest legal input: operator / partnership ids {@code VARCHAR(36)}, tenant ids
 * {@code VARCHAR(32)}, epoch millis 13 digits (until the year 2286).
 */
final class AuditIdempotencyKeys {

    /** {@code admin_actions.idempotency_key VARCHAR(100)} ({@code V0001__create_admin_actions.sql}). */
    static final int COLUMN = 100;

    private AuditIdempotencyKeys() {
    }

    /** Assignment create / delete / company exit. Max 4 + 36 + 1 + 32 + 1 + 13 = 87. */
    static String assignment(String operatorPublicId, String tenantId, Instant at) {
        return "asg:" + operatorPublicId + ":" + tenantId + ":" + at.toEpochMilli();
    }

    /** Org-scope update. Max 10 + 36 + 1 + 32 + 1 + 13 = 93. */
    static String orgScope(String operatorPublicId, String tenantId, Instant at) {
        return "org-scope:" + operatorPublicId + ":" + tenantId + ":" + at.toEpochMilli();
    }

    /**
     * Partnership participant add / remove. Max 4 + 36 + 1 + 36 + 1 + 13 = 91. The pre-634 form
     * {@code "participant:" + actionCode + ":" + …} was 127 (add) / 130 (remove) — over the column on every call.
     */
    static String participant(String partnershipId, String operatorPublicId, Instant at) {
        return "ptc:" + partnershipId + ":" + operatorPublicId + ":" + at.toEpochMilli();
    }

    /** Partnership invite / accept / suspend / reactivate / terminate. Max 12 + 22 + 1 + 36 + 1 + 13 = 85. */
    static String partnership(ActionCode code, String partnershipId, Instant at) {
        return "partnership:" + code.name() + ":" + partnershipId + ":" + at.toEpochMilli();
    }

    /** Identity link. Max 14 + 36 + 1 + 13 = 64. */
    static String identityLink(String operatorPublicId, Instant at) {
        return "identity-link:" + operatorPublicId + ":" + at.toEpochMilli();
    }

    /** Identity unlink. Max 16 + 36 + 1 + 13 = 66. */
    static String identityUnlink(String operatorPublicId, Instant at) {
        return "identity-unlink:" + operatorPublicId + ":" + at.toEpochMilli();
    }

    /** Profile update by an admin. Max 21 + 36 + 1 + 13 = 71. */
    static String adminProfileUpdate(String operatorPublicId, Instant at) {
        return "admin-profile-update:" + operatorPublicId + ":" + at.toEpochMilli();
    }

    /** Own-profile update. Max 20 + 36 + 1 + 13 = 70. */
    static String selfProfileUpdate(String operatorPublicId, Instant at) {
        return "self-profile-update:" + operatorPublicId + ":" + at.toEpochMilli();
    }
}
