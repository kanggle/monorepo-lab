package com.example.fanplatform.membership.domain.pricing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure upgrade-proration math (TASK-FAN-BE-032). The SAME method backs both the
 * quote the client pays and the charge the backend re-verifies, so these values
 * are the contract — a drift here wrongfully declines the PortOne payment.
 */
class UpgradeProrationTest {

    private static final long PREMIUM = MembershipPricing.PREMIUM_MONTHLY_MINOR;   // 17_900
    private static final long MEMBERS = MembershipPricing.MEMBERS_ONLY_MONTHLY_MINOR; // 7_900

    @Test
    void proratesWholeDaysAgainstPremiumListPrice() {
        // 15 remaining members days, 1 month premium: credit = 15*7900/30 = 3950
        UpgradeProration.Quote q = UpgradeProration.quote(15, 1, PREMIUM, MEMBERS);
        assertThat(q.listPriceMinor()).isEqualTo(17_900);
        assertThat(q.creditMinor()).isEqualTo(3_950);
        assertThat(q.chargeMinor()).isEqualTo(13_950);
        assertThat(q.remainingDays()).isEqualTo(15);
    }

    @Test
    void zeroRemainingDaysNoCredit() {
        UpgradeProration.Quote q = UpgradeProration.quote(0, 1, PREMIUM, MEMBERS);
        assertThat(q.creditMinor()).isZero();
        assertThat(q.chargeMinor()).isEqualTo(17_900);
    }

    @Test
    void negativeRemainingTreatedAsZero() {
        UpgradeProration.Quote q = UpgradeProration.quote(-5, 1, PREMIUM, MEMBERS);
        assertThat(q.creditMinor()).isZero();
        assertThat(q.chargeMinor()).isEqualTo(17_900);
    }

    @Test
    void creditCappedAtListPriceNeverNegativeCharge() {
        // 100 members days → raw credit 100*7900/30 = 26_333 > 17_900 → capped, charge 0
        UpgradeProration.Quote q = UpgradeProration.quote(100, 1, PREMIUM, MEMBERS);
        assertThat(q.creditMinor()).isEqualTo(17_900);
        assertThat(q.chargeMinor()).isZero();
    }

    @Test
    void multiMonthPremiumListPrice() {
        // 30 members days, 3 months premium: credit = 30*7900/30 = 7900; list = 53_700
        UpgradeProration.Quote q = UpgradeProration.quote(30, 3, PREMIUM, MEMBERS);
        assertThat(q.listPriceMinor()).isEqualTo(53_700);
        assertThat(q.creditMinor()).isEqualTo(7_900);
        assertThat(q.chargeMinor()).isEqualTo(45_800);
    }
}
