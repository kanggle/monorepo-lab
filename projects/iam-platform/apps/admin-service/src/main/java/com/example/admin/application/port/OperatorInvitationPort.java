package com.example.admin.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * TASK-MONO-772 S2 (ADR-MONO-080 D6 · R4) — persistence of {@code operator_invitation} for the application layer
 * (the JPA entity stays in infrastructure, the existing port pattern).
 *
 * <p>🔴 No method takes or returns the raw token — only its SHA-256 hex. The use case is the only place the raw
 * token exists, and only in memory between minting it and handing it to the mail port.
 */
public interface OperatorInvitationPort {

    /** A new {@code PENDING} row. */
    record NewInvitation(String invitationId, String tenantId, String email, String displayName,
                         List<String> roles, String tokenHash, Instant expiresAt, long invitedByInternalId,
                         Instant now) {}

    /**
     * One invitation as the use case sees it. {@code invitedByOperatorId} / {@code acceptedOperatorId} are the
     * external operator UUIDs (the API never exposes internal ids). {@code tokenHash} is carried only so the
     * delivery result can be keyed on the token it describes — it never reaches a response.
     */
    record InvitationView(long internalId, String invitationId, String tenantId, String email,
                          String displayName, List<String> roles, String status, Instant expiresAt,
                          long invitedByInternalId, String invitedByOperatorId,
                          String lastDeliveryStatus, Instant lastDeliveryAt,
                          Instant acceptedAt, String acceptedOperatorId,
                          Instant cancelledAt, Instant createdAt, int version, String tokenHash,
                          /**
                           * TASK-MONO-772 S3 — the pool account that accepted ({@code accepted_account_id}), or
                           * {@code null} while not accepted. The acceptance's «same account resubmits → 200» rule
                           * compares against it.
                           */
                          String acceptedAccountId) {

        /** The S2 shape — not accepted ({@code acceptedAccountId = null}). */
        public InvitationView(long internalId, String invitationId, String tenantId, String email,
                              String displayName, List<String> roles, String status, Instant expiresAt,
                              long invitedByInternalId, String invitedByOperatorId,
                              String lastDeliveryStatus, Instant lastDeliveryAt,
                              Instant acceptedAt, String acceptedOperatorId,
                              Instant cancelledAt, Instant createdAt, int version, String tokenHash) {
            this(internalId, invitationId, tenantId, email, displayName, roles, status, expiresAt,
                    invitedByInternalId, invitedByOperatorId, lastDeliveryStatus, lastDeliveryAt,
                    acceptedAt, acceptedOperatorId, cancelledAt, createdAt, version, tokenHash, null);
        }
    }

    record InvitationPage(List<InvitationView> content, long totalElements, int page, int size, int totalPages) {}

    /** Is there a {@code PENDING} row (expired included) for this tenant + normalised email? */
    boolean existsPending(String tenantId, String email);

    /**
     * Inserts and flushes. A concurrent INSERT that already holds the {@code (tenant_id, email)} PENDING slot
     * (the {@code uk_operator_invitation_pending_key} unique key) surfaces as
     * {@link com.example.admin.application.exception.OperatorInvitationAlreadyPendingException}.
     */
    InvitationView create(NewInvitation row);

    Optional<InvitationView> findByInvitationId(String invitationId);

    /** {@code tenantId == null} ⇒ every tenant (platform scope only — the caller decides). */
    InvitationPage findPage(String tenantId, String status, int page, int size);

    /** @return {@code true} when this call moved the row {@code PENDING → CANCELLED}. */
    boolean cancelIfPending(long internalId, long cancelledByInternalId, Instant at);

    /** @return {@code true} when this call rotated the token (row still PENDING at {@code expectedVersion}). */
    boolean rotateIfPending(long internalId, int expectedVersion, String newTokenHash, Instant newExpiresAt,
                            long invitedByInternalId, Instant at);

    /** Records the result of the mail for the token whose hash is {@code tokenHash} (no-op if since rotated). */
    void recordDelivery(long internalId, String tokenHash, String deliveryStatus, Instant at);

    // ── TASK-MONO-772 S3 — the acceptance ───────────────────────────────────

    /**
     * The invitation whose CURRENT token hashes to {@code tokenHash} (any status). A token a resend replaced
     * hashes to nothing — the old link is dead.
     */
    Optional<InvitationView> findByTokenHash(String tokenHash);

    /**
     * The acceptance's claim — {@code PENDING → ACCEPTED} with {@code accepted_at} / {@code accepted_account_id},
     * guarded by status AND the token hash that was presented AND «not expired at {@code at}» (data-model.md
     * invariant). 0 rows ⇒ a concurrent accept / cancel / resend won, or the invitation expired meanwhile.
     *
     * @return {@code true} when this call made the transition
     */
    boolean acceptIfPending(long internalId, String tokenHash, String accountId, Instant at);

    /** Writes {@code accepted_operator_id} on a row this transaction just accepted. */
    void recordAcceptedOperator(long internalId, long operatorInternalId, Instant at);
}
