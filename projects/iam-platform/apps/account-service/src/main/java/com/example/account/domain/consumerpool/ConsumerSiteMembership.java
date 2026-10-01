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
 */
public final class ConsumerSiteMembership {

    private final String accountId;
    private final TenantId siteTenantId;
    private final ConsumerSiteMembershipStatus status;
    private final Instant consentedAt;

    private ConsumerSiteMembership(String accountId, TenantId siteTenantId,
                                   ConsumerSiteMembershipStatus status, Instant consentedAt) {
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
    }

    /**
     * Signing up on a site is consenting to use that site (contract § 2), so the membership is
     * born {@link ConsumerSiteMembershipStatus#ACTIVE} with {@code consentedAt} = the signup time.
     */
    public static ConsumerSiteMembership joinOnSignup(String accountId, TenantId siteTenantId,
                                                      Instant signedUpAt) {
        return new ConsumerSiteMembership(accountId, siteTenantId,
                ConsumerSiteMembershipStatus.ACTIVE, signedUpAt);
    }

    /**
     * TASK-BE-616 (contract § 4) — a pool account's first visit to ANOTHER consumer site: the person
     * accepted that site's one-screen consent, so the membership is born
     * {@link ConsumerSiteMembershipStatus#ACTIVE} with {@code consentedAt} = the moment of consent.
     */
    public static ConsumerSiteMembership joinOnConsent(String accountId, TenantId siteTenantId,
                                                       Instant consentedAt) {
        return new ConsumerSiteMembership(accountId, siteTenantId,
                ConsumerSiteMembershipStatus.ACTIVE, consentedAt);
    }

    public static ConsumerSiteMembership reconstitute(String accountId, TenantId siteTenantId,
                                                      ConsumerSiteMembershipStatus status,
                                                      Instant consentedAt) {
        return new ConsumerSiteMembership(accountId, siteTenantId, status, consentedAt);
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

    public boolean isActive() {
        return status == ConsumerSiteMembershipStatus.ACTIVE;
    }
}
