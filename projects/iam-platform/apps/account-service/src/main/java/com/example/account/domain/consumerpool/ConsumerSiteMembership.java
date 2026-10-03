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
 */
public final class ConsumerSiteMembership {

    private final String accountId;
    private final TenantId siteTenantId;
    private final ConsumerSiteMembershipStatus status;
    private final Instant consentedAt;
    /** TASK-BE-619 — null while ACTIVE. */
    private final Instant leftAt;
    /** TASK-BE-619 — null while ACTIVE. A LEFT row without it (none exist — no writer before 619) reads as not reopenable. */
    private final ConsumerSiteLeftBy leftBy;
    /** TASK-BE-619 — the operator id for {@link ConsumerSiteLeftBy#OPERATOR}, the account id for SELF. */
    private final String leftByActorId;

    private ConsumerSiteMembership(String accountId, TenantId siteTenantId,
                                   ConsumerSiteMembershipStatus status, Instant consentedAt,
                                   Instant leftAt, ConsumerSiteLeftBy leftBy, String leftByActorId) {
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
        if (status == ConsumerSiteMembershipStatus.ACTIVE && (leftAt != null || leftBy != null)) {
            throw new IllegalArgumentException("an ACTIVE membership carries no leave record");
        }
        this.leftAt = leftAt;
        this.leftBy = leftBy;
        this.leftByActorId = leftByActorId;
    }

    /**
     * Signing up on a site is consenting to use that site (contract § 2), so the membership is
     * born {@link ConsumerSiteMembershipStatus#ACTIVE} with {@code consentedAt} = the signup time.
     */
    public static ConsumerSiteMembership joinOnSignup(String accountId, TenantId siteTenantId,
                                                      Instant signedUpAt) {
        return new ConsumerSiteMembership(accountId, siteTenantId,
                ConsumerSiteMembershipStatus.ACTIVE, signedUpAt, null, null, null);
    }

    /**
     * TASK-BE-616 (contract § 4) — a pool account's first visit to ANOTHER consumer site: the person
     * accepted that site's one-screen consent, so the membership is born
     * {@link ConsumerSiteMembershipStatus#ACTIVE} with {@code consentedAt} = the moment of consent.
     */
    public static ConsumerSiteMembership joinOnConsent(String accountId, TenantId siteTenantId,
                                                       Instant consentedAt) {
        return new ConsumerSiteMembership(accountId, siteTenantId,
                ConsumerSiteMembershipStatus.ACTIVE, consentedAt, null, null, null);
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
        return new ConsumerSiteMembership(accountId, siteTenantId, status, consentedAt,
                leftAt, leftBy, leftByActorId);
    }

    /**
     * TASK-BE-619 — this membership after {@code by} made it LEFT at {@code at}.
     *
     * <ul>
     *   <li>ACTIVE → LEFT, recording who and when.</li>
     *   <li>Already LEFT by {@link ConsumerSiteLeftBy#SELF}, now removed by the site's
     *       {@link ConsumerSiteLeftBy#OPERATOR} → re-recorded as OPERATOR: the operator's removal must
     *       stick, so the person can no longer reopen it by consent.</li>
     *   <li>Anything else already LEFT → unchanged (the same object): leaving twice is not a new event,
     *       and a person leaving cannot downgrade an operator's removal to a reopenable one.</li>
     * </ul>
     */
    public ConsumerSiteMembership leave(ConsumerSiteLeftBy by, String actorId, Instant at) {
        Objects.requireNonNull(by, "by is required");
        Objects.requireNonNull(at, "at is required");
        boolean escalate = status == ConsumerSiteMembershipStatus.LEFT
                && leftBy == ConsumerSiteLeftBy.SELF && by == ConsumerSiteLeftBy.OPERATOR;
        if (status == ConsumerSiteMembershipStatus.ACTIVE || escalate) {
            return new ConsumerSiteMembership(accountId, siteTenantId, ConsumerSiteMembershipStatus.LEFT,
                    consentedAt, at, by, actorId);
        }
        return this;
    }

    /**
     * TASK-BE-619 (owner decision 2026-10-03 «다시 동의하면 복귀») — a membership the person LEFT
     * themself comes back by consenting again; one the site's operator removed does not (and a LEFT row
     * with no recorded actor is treated like the operator's — the conservative side).
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
                Objects.requireNonNull(at, "at is required"), null, null, null);
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

    public boolean isActive() {
        return status == ConsumerSiteMembershipStatus.ACTIVE;
    }
}
