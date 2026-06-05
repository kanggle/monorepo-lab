package com.example.erp.readmodel.domain.delegation;

import java.time.Instant;
import java.util.Objects;

/**
 * Projection of the <b>latest fact</b> of a delegation grant (read-only, E5;
 * TASK-ERP-BE-015). Maintained by the {@code erp.approval.delegated.v1} +
 * {@code erp.approval.delegation.revoked.v1} consumers as a single-row upsert
 * keyed by {@code grantId} (= aggregateId). It holds the current status
 * (ACTIVE/REVOKED) + the delegator/delegate ids + the validity window + last
 * reason + revoke timestamp — <b>NOT</b> the authoritative grant audit history
 * (which stays with {@code approval-service}). Pure Java — no framework
 * annotations (Hexagonal domain).
 *
 * <p><b>Sticky-terminal REVOKED (last-event-wins).</b> Once the projection
 * reaches {@code REVOKED}, a later non-duplicate {@code delegated} event never
 * reverts it to {@code ACTIVE}: {@link #applyGrant} is a status no-op on a
 * terminal (REVOKED) row — mirroring {@code ApprovalFactProjection}'s
 * terminal-once guard. The producer's per-{@code grantId} partition ordering
 * means {@code delegated} normally precedes its {@code revoked}; the guard only
 * protects against replay / out-of-contract delivery. {@link #applyRevoke} is
 * last-revoke-wins (stays REVOKED).
 *
 * <p><b>Out-of-order tolerance.</b> A {@code revoked} arriving with no prior
 * {@code delegated} (compaction / replay-from-middle) still produces a row via
 * {@link #ofRevoked}; the grant-only fields ({@code validFrom}/{@code validTo})
 * are left {@code null} (ABSENT — never fabricated, E5; the revoke payload does
 * not carry the validity window). dedupe (eventId) prevents true duplicates.
 */
public final class DelegationFactProjection {

    private final String grantId;
    private DelegationFactStatus status;
    private String delegatorId;
    private String delegateId;
    private Instant validFrom;
    private Instant validTo;
    private String reason;
    private Instant revokedAt;
    private Instant lastEventAt;
    private String lastEventId;

    public DelegationFactProjection(String grantId, DelegationFactStatus status,
                                    String delegatorId, String delegateId,
                                    Instant validFrom, Instant validTo, String reason,
                                    Instant revokedAt, Instant lastEventAt, String lastEventId) {
        this.grantId = Objects.requireNonNull(grantId, "grantId");
        this.status = status;
        this.delegatorId = delegatorId;
        this.delegateId = delegateId;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.reason = reason;
        this.revokedAt = revokedAt;
        this.lastEventAt = lastEventAt;
        this.lastEventId = lastEventId;
    }

    /**
     * Factory for a brand-new {@code ACTIVE} fact (the grant's first projected
     * event in normal ordering — the {@code delegated} event).
     */
    public static DelegationFactProjection ofGranted(String grantId, String delegatorId,
                                                     String delegateId, Instant validFrom,
                                                     Instant validTo, String reason,
                                                     Instant lastEventAt, String lastEventId) {
        return new DelegationFactProjection(grantId, DelegationFactStatus.ACTIVE,
                delegatorId, delegateId, validFrom, validTo, reason,
                null, lastEventAt, lastEventId);
    }

    /**
     * Factory for a {@code REVOKED} fact produced when the {@code revoked} event
     * arrives with no prior {@code delegated} row (out-of-order). The grant-only
     * fields ({@code validFrom}/{@code validTo}) are left {@code null} (ABSENT —
     * no fabrication, E5; the revoke payload carries no validity window).
     * {@code revokedAt} = the event instant.
     */
    public static DelegationFactProjection ofRevoked(String grantId, String delegatorId,
                                                     String delegateId, String reason,
                                                     Instant revokedAt, Instant lastEventAt,
                                                     String lastEventId) {
        return new DelegationFactProjection(grantId, DelegationFactStatus.REVOKED,
                delegatorId, delegateId, null, null, reason,
                revokedAt, lastEventAt, lastEventId);
    }

    /**
     * Applies a {@code delegated} (grant) event to an EXISTING row. Sticky-terminal:
     * a no-op on {@code status} when the row is already REVOKED — never reverts a
     * REVOKED row to ACTIVE. On a non-terminal row it (re)stamps ACTIVE. The
     * delegator/delegate ids + the validity window + reason are refreshed from the
     * grant payload (the {@code delegated} event is authoritative for the window —
     * including filling in an out-of-order row whose window was ABSENT). Always
     * advances the provenance timestamps for a non-duplicate event.
     */
    public void applyGrant(String delegatorId, String delegateId, Instant validFrom,
                           Instant validTo, String reason,
                           Instant lastEventAt, String lastEventId) {
        this.delegatorId = delegatorId;
        this.delegateId = delegateId;
        this.validFrom = validFrom;
        this.validTo = validTo;
        if (reason != null) {
            this.reason = reason;
        }
        if (!isTerminal()) {
            this.status = DelegationFactStatus.ACTIVE;
        }
        this.lastEventAt = lastEventAt;
        this.lastEventId = lastEventId;
    }

    /**
     * Applies a {@code revoked} event to an EXISTING row. Last-revoke-wins: sets
     * {@code status = REVOKED} + {@code revokedAt}; the validity window is
     * preserved (the revoke event does not restate it). The delegator/delegate
     * ids are refreshed (the revoke payload carries them). {@code reason} is
     * updated when supplied.
     */
    public void applyRevoke(String delegatorId, String delegateId, String reason,
                            Instant revokedAt, Instant lastEventAt, String lastEventId) {
        this.status = DelegationFactStatus.REVOKED;
        this.delegatorId = delegatorId;
        this.delegateId = delegateId;
        if (reason != null) {
            this.reason = reason;
        }
        if (revokedAt != null) {
            this.revokedAt = revokedAt;
        }
        this.lastEventAt = lastEventAt;
        this.lastEventId = lastEventId;
    }

    public boolean isTerminal() {
        return status != null && status.isTerminal();
    }

    /**
     * {@code true} iff the grant authorizes a delegated action at {@code t}:
     * {@code status == ACTIVE} AND {@code validFrom <= t} AND ({@code validTo} is
     * null OR {@code t <= validTo}). A row whose {@code validFrom} is ABSENT
     * (out-of-order revoke before grant) is never active (status is REVOKED there
     * anyway).
     */
    public boolean isActiveAt(Instant t) {
        Objects.requireNonNull(t, "t");
        return status == DelegationFactStatus.ACTIVE
                && validFrom != null
                && !t.isBefore(validFrom)
                && (validTo == null || !t.isAfter(validTo));
    }

    public String grantId() { return grantId; }
    public DelegationFactStatus status() { return status; }
    public String delegatorId() { return delegatorId; }
    public String delegateId() { return delegateId; }
    public Instant validFrom() { return validFrom; }
    public Instant validTo() { return validTo; }
    public String reason() { return reason; }
    public Instant revokedAt() { return revokedAt; }
    public Instant lastEventAt() { return lastEventAt; }
    public String lastEventId() { return lastEventId; }
}
