package com.example.fanplatform.membership.presentation.dto;

import com.example.fanplatform.membership.application.billing.BillingKeyEnrollmentView;

/**
 * Billing-key cancel (auto-renew off) response — {@code { tier, active: false }}.
 */
public record BillingKeyCancelResponse(
        String tier,
        boolean active) {

    public static BillingKeyCancelResponse from(BillingKeyEnrollmentView v) {
        return new BillingKeyCancelResponse(v.tier().name(), v.active());
    }
}
