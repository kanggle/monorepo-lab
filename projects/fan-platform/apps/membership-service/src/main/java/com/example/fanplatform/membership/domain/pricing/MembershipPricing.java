package com.example.fanplatform.membership.domain.pricing;

import com.example.fanplatform.membership.domain.membership.MembershipTier;

/**
 * Tier-aware monthly price in minor units (KRW won — KRW has no sub-unit, so
 * "minor" == won). Replaces the former flat {@code PRICE_PER_MONTH_MINOR = 9_900}
 * so the charge matches the tiers' displayed prices and the
 * MEMBERS_ONLY → PREMIUM upgrade credit (TASK-FAN-BE-032) is meaningful.
 *
 * <p>Pure domain constants — no framework deps. The PortOne adapter verifies the
 * PortOne-paid amount equals what these produce (ADR-001 amount-tamper guard).
 */
public final class MembershipPricing {

    private MembershipPricing() {
    }

    public static final long MEMBERS_ONLY_MONTHLY_MINOR = 7_900L;
    public static final long PREMIUM_MONTHLY_MINOR = 17_900L;

    /** Whole days used to prorate a monthly price (fixed 30-day month). */
    public static final int DAYS_PER_MONTH = 30;

    /** Monthly list price for a tier. */
    public static long monthlyMinor(MembershipTier tier) {
        return switch (tier) {
            case MEMBERS_ONLY -> MEMBERS_ONLY_MONTHLY_MINOR;
            case PREMIUM -> PREMIUM_MONTHLY_MINOR;
        };
    }

    /** Plain (non-upgrade) charge for {@code planMonths} of a tier. */
    public static long listChargeMinor(MembershipTier tier, int planMonths) {
        return monthlyMinor(tier) * planMonths;
    }
}
