package com.example.account.domain.consumerpool;

import com.example.account.domain.tenant.TenantId;

import java.time.Instant;
import java.util.Objects;

/**
 * TASK-BE-614 (ADR-MONO-078 A, multi-tenancy.md § 소비자 계정 풀 § 1) — a pool account's entry
 * into one consumer site. Row of {@code consumer_site_memberships}, PK {@code (account_id, site_tenant_id)}.
 *
 * <p>The account itself lives in {@link TenantId#CONSUMER_POOL}; this value says <i>which
 * site</i> it may be used on. A site without a membership gets no token and shows the first-visit
 * consent screen instead ({@code TASK-BE-616}).
 *
 * <p>Invariant enforced here (the data model leaves it to the application): the site is never the
 * pool tenant itself.
 *
 * <p><b>Leaving one site (TASK-BE-619).</b> {@link #leave} makes the membership
 * {@link ConsumerSiteMembershipStatus#LEFT} and records who did it ({@link ConsumerSiteLeftBy}). It
 * never touches the account — leaving a site is not deleting the account (contract § 5). A LEFT
 * membership admits no token for that site (TASK-BE-615); whether consent can bring it back depends
 * on who left ({@link #isReopenableByConsent}).
 *
 * <p><b>Locked on one site (TASK-BE-621).</b> {@link #lockBySiteOperator} makes the membership
 * {@link ConsumerSiteMembershipStatus#LOCKED} — that site's operator locked the person out of THAT site
 * only (owner decision 2026-10-04). Like LEFT it admits no token for the site, and consent does not reopen
 * it; unlike LEFT it is temporary: {@link #unlockBySiteOperator} makes it ACTIVE again, site roles intact.
 * The person cannot shed a lock by leaving themself and consenting again ({@link #leave} with
 * {@link ConsumerSiteLeftBy#SELF} leaves a LOCKED membership as it is).
 */
public final class ConsumerSiteMembership {

    private final String accountId;
    private final TenantId siteTenantId;
    private final ConsumerSiteMembershipStatus status;
    private final Instant consentedAt;
    /** TASK-BE-619 — null unless LEFT. */
    private final Instant leftAt;
    /** TASK-BE-619 — null unless LEFT. A LEFT row without it (none exist — no writer before 619) reads as not reopenable. */
    private final ConsumerSiteLeftBy leftBy;
    /** TASK-BE-619 — the operator id for {@link ConsumerSiteLeftBy#OPERATOR}, the account id for SELF. */
    private final String leftByActorId;
    /** TASK-BE-621 — when the site's operator locked it; non-null iff LOCKED. */
    private final Instant lockedAt;
    /** TASK-BE-621 — the operator id that locked it; null unless LOCKED. */
    private final String lockedByActorId;

    private ConsumerSiteMembership(String accountId, TenantId siteTenantId,
                                   ConsumerSiteMembershipStatus status, Instant consentedAt,
                                   Instant leftAt, ConsumerSiteLeftBy leftBy, String leftByActorId,
                                   Instant lockedAt, String lockedByActorId) {
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("accountId is required");
        }
        Objects.requireNonNull(siteTenantId, "siteTenantId is required");
        if (siteTenantId.isConsumerPool()) {
            throw new IllegalArgumentException(
                    "a membership's site cannot be the pool tenant itself: " + siteTenantId.value());
        }
        this.accountId = accountId;
        this.siteTenantId = siteTenantId;
        this.status = Objects.requireNonNull(status, "status is required");
        this.consentedAt = Objects.requireNonNull(consentedAt, "consentedAt is required");
        if (status != ConsumerSiteMembershipStatus.LEFT && (leftAt != null || leftBy != null)) {
            throw new IllegalArgumentException("only a LEFT membership carries a leave record: status=" + status);
        }
        if (status == ConsumerSiteMembershipStatus.LOCKED && lockedAt == null) {
            throw new IllegalArgumentException("a LOCKED membership carries its lock time");
        }
        if (status != ConsumerSiteMembershipStatus.LOCKED && (lockedAt != null || lockedByActorId != null)) {
            throw new IllegalArgumentException("only a LOCKED membership carries a lock record: status=" + status);
        }
        this.leftAt = leftAt;
        this.leftBy = leftBy;
        this.leftByActorId = leftByActorId;
        this.lockedAt = lockedAt;
        this.lockedByActorId = lockedByActorId;
    }

    /**
     * Signing up on a site is consenting to use that site (contract § 2), so the membership is
     * born {@link ConsumerSiteMembershipStatus#ACTIVE} with {@code consentedAt} = the signup time.
     */
    public static ConsumerSiteMembership joinOnSignup(String accountId, TenantId siteTenantId,
                                                      Instant signedUpAt) {
        return new ConsumerSiteMembership(accountId, siteTenantId,
                ConsumerSiteMembershipStatus.ACTIVE, signedUpAt, null, null, null, null, null);
    }

    /**
     * TASK-BE-616 (contract § 4) — a pool account's first visit to ANOTHER consumer site: the person
     * accepted that site's one-screen consent, so the membership is born
     * {@link ConsumerSiteMembershipStatus#ACTIVE} with {@code consentedAt} = the moment of consent.
     */
    public static ConsumerSiteMembership joinOnConsent(String accountId, TenantId siteTenantId,
                                                       Instant consentedAt) {
        return new ConsumerSiteMembership(accountId, siteTenantId,
                ConsumerSiteMembershipStatus.ACTIVE, consentedAt, null, null, null, null, null);
    }

    public static ConsumerSiteMembership reconstitute(String accountId, TenantId siteTenantId,
                                                      ConsumerSiteMembershipStatus status,
                                                      Instant consentedAt) {
        return reconstitute(accountId, siteTenantId, status, consentedAt, null, null, null);
    }

    /** TASK-BE-619 — with the leave record (V0032 columns). */
    public static ConsumerSiteMembership reconstitute(String accountId, TenantId siteTenantId,
                                                      ConsumerSiteMembershipStatus status,
                                                      Instant consentedAt, Instant leftAt,
                                                      ConsumerSiteLeftBy leftBy, String leftByActorId) {
        return reconstitute(accountId, siteTenantId, status, consentedAt, leftAt, leftBy, leftByActorId,
                null, null);
    }

    /** TASK-BE-621 — with the leave record (V0032) and the lock record (V0033). */
    public static ConsumerSiteMembership reconstitute(String accountId, TenantId siteTenantId,
                                                      ConsumerSiteMembershipStatus status,
                                                      Instant consentedAt, Instant leftAt,
                                                      ConsumerSiteLeftBy leftBy, String leftByActorId,
                                                      Instant lockedAt, String lockedByActorId) {
        return new ConsumerSiteMembership(accountId, siteTenantId, status, consentedAt,
                leftAt, leftBy, leftByActorId, lockedAt, lockedByActorId);
    }

    /**
     * TASK-BE-619 — this membership after {@code by} made it LEFT at {@code at}.
     *
     * <ul>
     *   <li>ACTIVE → LEFT, recording who and when.</li>
     *   <li>Already LEFT by {@link ConsumerSiteLeftBy#SELF}, now removed by the site's
     *       {@link ConsumerSiteLeftBy#OPERATOR} → re-recorded as OPERATOR: the operator's removal must
     *       stick, so the person can no longer reopen it by consent.</li>
     *   <li>TASK-BE-621 — LOCKED, removed by the site's OPERATOR → LEFT (OPERATOR), the lock record
     *       cleared: the removal is the stronger, final state.</li>
     *   <li>TASK-BE-621 — LOCKED, the person leaving themself (SELF) → unchanged: leaving and consenting
     *       again must not be a way out of a lock.</li>
     *   <li>Anything else already LEFT → unchanged (the same object): leaving twice is not a new event,
     *       and a person leaving cannot downgrade an operator's removal to a reopenable one.</li>
     * </ul>
     */
    public ConsumerSiteMembership leave(ConsumerSiteLeftBy by, String actorId, Instant at) {
        Objects.requireNonNull(by, "by is required");
        Objects.requireNonNull(at, "at is required");
        boolean escalate = status == ConsumerSiteMembershipStatus.LEFT
                && leftBy == ConsumerSiteLeftBy.SELF && by == ConsumerSiteLeftBy.OPERATOR;
        boolean removeLocked = status == ConsumerSiteMembershipStatus.LOCKED && by == ConsumerSiteLeftBy.OPERATOR;
        if (status == ConsumerSiteMembershipStatus.ACTIVE || escalate || removeLocked) {
            return new ConsumerSiteMembership(accountId, siteTenantId, ConsumerSiteMembershipStatus.LEFT,
                    consentedAt, at, by, actorId, null, null);
        }
        return this;
    }

    /**
     * TASK-BE-621 (owner decision 2026-10-04 «사이트 운영자가 회원을 잠글 때, 그 잠금은 자기 사이트에만 걸린다») —
     * this membership after that site's operator locked the person out of the site.
     *
     * <ul>
     *   <li>ACTIVE → LOCKED, recording who and when.</li>
     *   <li>Already LOCKED → unchanged (the same object): idempotent, like the account state machine's
     *       same-state rule.</li>
     * </ul>
     *
     * @throws IllegalStateException when the membership is LEFT — there is nothing on the site to lock
     */
    public ConsumerSiteMembership lockBySiteOperator(String operatorId, Instant at) {
        Objects.requireNonNull(at, "at is required");
        if (status == ConsumerSiteMembershipStatus.LOCKED) {
            return this;
        }
        if (status != ConsumerSiteMembershipStatus.ACTIVE) {
            throw new IllegalStateException("only an ACTIVE membership can be locked: status=" + status);
        }
        return new ConsumerSiteMembership(accountId, siteTenantId, ConsumerSiteMembershipStatus.LOCKED,
                consentedAt, null, null, null, at, operatorId);
    }

    /**
     * TASK-BE-621 — this membership after that site's operator lifted their lock: LOCKED → ACTIVE, the lock
     * record cleared, {@code consentedAt} kept (the person consented once; the lock did not withdraw it).
     * Already ACTIVE → unchanged (idempotent).
     *
     * @throws IllegalStateException when the membership is LEFT
     */
    public ConsumerSiteMembership unlockBySiteOperator() {
        if (status == ConsumerSiteMembershipStatus.ACTIVE) {
            return this;
        }
        if (status != ConsumerSiteMembershipStatus.LOCKED) {
            throw new IllegalStateException("only a LOCKED membership can be unlocked: status=" + status);
        }
        return new ConsumerSiteMembership(accountId, siteTenantId, ConsumerSiteMembershipStatus.ACTIVE,
                consentedAt, null, null, null, null, null);
    }

    /**
     * TASK-BE-619 (owner decision 2026-10-03 «다시 동의하면 복귀») — a membership the person LEFT
     * themself comes back by consenting again; one the site's operator removed does not (and a LEFT row
     * with no recorded actor is treated like the operator's — the conservative side). A LOCKED membership
     * (TASK-BE-621) is never reopenable by consent.
     */
    public boolean isReopenableByConsent() {
        return status == ConsumerSiteMembershipStatus.LEFT && leftBy == ConsumerSiteLeftBy.SELF;
    }

    /**
     * TASK-BE-619 — the person consented to the site again after leaving it themself: ACTIVE again,
     * {@code consentedAt} = this consent, the leave record cleared.
     *
     * @throws IllegalStateException when the membership is not {@link #isReopenableByConsent() reopenable}
     */
    public ConsumerSiteMembership rejoinOnConsent(Instant at) {
        if (!isReopenableByConsent()) {
            throw new IllegalStateException("membership is not reopenable by consent: status=" + status
                    + " leftBy=" + leftBy);
        }
        return new ConsumerSiteMembership(accountId, siteTenantId, ConsumerSiteMembershipStatus.ACTIVE,
                Objects.requireNonNull(at, "at is required"), null, null, null, null, null);
    }

    public String getAccountId() {
        return accountId;
    }

    public TenantId getSiteTenantId() {
        return siteTenantId;
    }

    public ConsumerSiteMembershipStatus getStatus() {
        return status;
    }

    public Instant getConsentedAt() {
        return consentedAt;
    }

    public Instant getLeftAt() {
        return leftAt;
    }

    public ConsumerSiteLeftBy getLeftBy() {
        return leftBy;
    }

    public String getLeftByActorId() {
        return leftByActorId;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public String getLockedByActorId() {
        return lockedByActorId;
    }

    public boolean isActive() {
        return status == ConsumerSiteMembershipStatus.ACTIVE;
    }

    /** TASK-BE-621 — locked on this site by its operator. */
    public boolean isLocked() {
        return status == ConsumerSiteMembershipStatus.LOCKED;
    }
}
