package com.example.fanplatform.membership.presentation.dto;

import com.example.fanplatform.membership.application.MembershipView;

import java.time.Instant;

/**
 * Full membership payload (subscribe / cancel / detail responses). Includes the
 * read-time {@code active} flag.
 */
public record MembershipResponse(
        String membershipId,
        String tenantId,
        String accountId,
        String tier,
        String status,
        Instant validFrom,
        Instant validTo,
        int planMonths,
        String paymentRef,
        boolean active,
        Instant createdAt,
        Instant canceledAt) {

    public static MembershipResponse from(MembershipView v) {
        return new MembershipResponse(
                v.membershipId(), v.tenantId(), v.accountId(),
                v.tier().name(), v.status().name(),
                v.validFrom(), v.validTo(), v.planMonths(), v.paymentRef(),
                v.active(), v.createdAt(), v.canceledAt());
    }
}
