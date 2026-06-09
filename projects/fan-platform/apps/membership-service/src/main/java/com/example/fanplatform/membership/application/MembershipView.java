package com.example.fanplatform.membership.application;

import com.example.fanplatform.membership.domain.access.AccessPolicy;
import com.example.fanplatform.membership.domain.membership.Membership;
import com.example.fanplatform.membership.domain.membership.MembershipTier;
import com.example.fanplatform.membership.domain.membership.status.MembershipStatus;

import java.time.Instant;

/**
 * Application read model for a single membership. {@code active} is the
 * read-time evaluation ({@code status == ACTIVE && now ∈ [validFrom, validTo]}) —
 * a stored-ACTIVE row past its window reads {@code active=false}.
 */
public record MembershipView(
        String membershipId,
        String tenantId,
        String accountId,
        MembershipTier tier,
        MembershipStatus status,
        Instant validFrom,
        Instant validTo,
        int planMonths,
        String paymentRef,
        boolean active,
        Instant createdAt,
        Instant canceledAt) {

    public static MembershipView from(Membership m, Instant now) {
        boolean active = m.getStatus() == MembershipStatus.ACTIVE && AccessPolicy.inWindow(m, now);
        return new MembershipView(
                m.getId(), m.getTenantId(), m.getAccountId(), m.getTier(), m.getStatus(),
                m.getValidFrom(), m.getValidTo(), m.getPlanMonths(), m.getPaymentRef(),
                active, m.getCreatedAt(), m.getCanceledAt());
    }
}
