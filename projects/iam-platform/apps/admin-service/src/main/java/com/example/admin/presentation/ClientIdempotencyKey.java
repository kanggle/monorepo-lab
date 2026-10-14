package com.example.admin.presentation;

/**
 * TASK-BE-635 — the length bound every client-sent {@code Idempotency-Key} header must carry.
 *
 * <p>{@code TASK-BE-634} bounded the keys admin-service builds <b>itself</b> ({@code AuditIdempotencyKeys}). This
 * class is the other kind: a key the <b>caller</b> sends, which admin-service stores verbatim (or near-verbatim —
 * see {@link #BULK_LOCK_HEADER_MAX}) into {@code admin_actions.idempotency_key VARCHAR(100)}
 * ({@code V0001__create_admin_actions.sql}). An over-length header used to make the column INSERT fail, and the
 * fail-closed audit write turned that client input error into {@code 500 AUDIT_FAILURE} instead of a
 * {@code 400 VALIDATION_ERROR}.
 *
 * <p>Public (not package-private, unlike {@code AuditIdempotencyKeys}): consumed from both
 * {@code com.example.admin.presentation} and {@code com.example.admin.presentation.tenant}.
 *
 * <p><b>A new {@code @RequestHeader("Idempotency-Key")} must bound the parameter with one of these constants
 * (or document in {@code ClientIdempotencyKeyTest} why it is checked downstream instead)</b> —
 * {@code ClientIdempotencyKeyTest#everyIdempotencyKeyHeaderCarriesALengthBound} fails otherwise.
 */
public final class ClientIdempotencyKey {

    /**
     * The column itself. Every single-key endpoint (lock, unlock, gdpr-delete, 2fa-reset, session-revoke,
     * operator-create, tenant-create) bounds its header to this directly.
     */
    public static final int MAX_LENGTH = 100;

    /**
     * Bulk-lock's per-row audit/downstream key is {@code header + ":" + accountId}
     * ({@code BulkLockAccountUseCase#processOne}). {@code accountId} is bounded to
     * {@link #BULK_LOCK_ACCOUNT_ID_MAX} ({@code accounts.id VARCHAR(36)}), so the header must leave room for the
     * separator + accountId: 100 − 1 − 36 = 63 (TASK-BE-635 — was 64, one character over the column at the
     * boundary). account-service does not read the {@code Idempotency-Key} header at all (grepped empty across
     * {@code apps/account-service}), so this bound changes no downstream retry semantics — only admin-service's
     * own {@code (actor_id, action_code, idempotency_key)} replay/audit key.
     */
    public static final int BULK_LOCK_HEADER_MAX = 63;

    /** {@code accounts.id VARCHAR(36)} — bulk-lock's {@code accountIds} element bound (see {@link #BULK_LOCK_HEADER_MAX}). */
    public static final int BULK_LOCK_ACCOUNT_ID_MAX = 36;

    /**
     * {@code UpdateTenantUseCase} appends {@code ":status"} (7 characters) to the header for the status-change
     * audit row when both displayName and status change in one PATCH. 100 − 7 = 93.
     */
    public static final int TENANT_UPDATE_HEADER_MAX = 93;

    private ClientIdempotencyKey() {
    }
}
