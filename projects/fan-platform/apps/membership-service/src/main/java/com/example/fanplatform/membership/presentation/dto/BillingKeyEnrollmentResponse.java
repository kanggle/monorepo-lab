package com.example.fanplatform.membership.presentation.dto;

import com.example.fanplatform.membership.application.billing.BillingKeyEnrollmentView;

import java.time.Instant;

/**
 * Billing-key enrollment response (issuance). <b>Never carries the billing key</b>
 * (ADR-002 §D5).
 */
public record BillingKeyEnrollmentResponse(
        String enrollmentId,
        String tier,
        boolean active,
        Instant createdAt) {

    public static BillingKeyEnrollmentResponse from(BillingKeyEnrollmentView v) {
        return new BillingKeyEnrollmentResponse(v.enrollmentId(), v.tier().name(), v.active(), v.createdAt());
    }
}
