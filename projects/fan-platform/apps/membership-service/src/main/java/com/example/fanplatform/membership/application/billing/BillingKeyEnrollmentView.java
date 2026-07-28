package com.example.fanplatform.membership.application.billing;

import com.example.fanplatform.membership.domain.billing.BillingKeyEnrollment;
import com.example.fanplatform.membership.domain.membership.MembershipTier;

import java.time.Instant;

/**
 * Read model for a billing-key enrollment. <b>Deliberately carries NO billing key</b>
 * (ADR-002 §D5 — the key is never returned in any response). {@code enrollmentId},
 * {@code tier}, {@code active}, {@code createdAt} only.
 */
public record BillingKeyEnrollmentView(
        String enrollmentId,
        MembershipTier tier,
        boolean active,
        Instant createdAt) {

    public static BillingKeyEnrollmentView from(BillingKeyEnrollment e) {
        return new BillingKeyEnrollmentView(e.getId(), e.getTier(), e.isActive(), e.getCreatedAt());
    }
}
