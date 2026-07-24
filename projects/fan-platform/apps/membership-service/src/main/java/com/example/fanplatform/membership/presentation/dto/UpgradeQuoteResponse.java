package com.example.fanplatform.membership.presentation.dto;

import com.example.fanplatform.membership.application.UpgradeQuoteView;

/**
 * Upgrade-quote response body (TASK-FAN-BE-032). Amounts are in minor units (KRW
 * won). {@code chargeMinor} is what the client requests from PortOne;
 * {@code supersedesMembershipId} is null for a plain subscribe (no credit).
 */
public record UpgradeQuoteResponse(
        String tier,
        int planMonths,
        long listPriceMinor,
        long creditMinor,
        long chargeMinor,
        String supersedesMembershipId) {

    public static UpgradeQuoteResponse from(UpgradeQuoteView v) {
        return new UpgradeQuoteResponse(
                v.tier().name(), v.planMonths(),
                v.listPriceMinor(), v.creditMinor(), v.chargeMinor(),
                v.supersedesMembershipId());
    }
}
