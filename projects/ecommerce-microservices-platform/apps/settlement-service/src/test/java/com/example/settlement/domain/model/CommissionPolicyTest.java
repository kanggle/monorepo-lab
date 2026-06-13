package com.example.settlement.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommissionPolicyTest {

    @Test
    void splits_gross_into_commission_and_seller_net_at_rate() {
        // 30000 minor @ 1000 bps (10%) → commission 3000, seller_net 27000 (AC-3)
        CommissionSplit split = CommissionPolicy.split(30_000L, 1000);

        assertThat(split.commissionMinor()).isEqualTo(3_000L);
        assertThat(split.sellerNetMinor()).isEqualTo(27_000L);
        assertThat(split.rateBps()).isEqualTo(1000);
    }

    @Test
    void commission_plus_seller_net_always_equals_gross() {
        // F1: the split invariant — seller_net is the remainder, never a drift.
        CommissionSplit split = CommissionPolicy.split(99_999L, 777);

        assertThat(split.commissionMinor() + split.sellerNetMinor()).isEqualTo(99_999L);
    }

    @Test
    void zero_rate_yields_no_commission_seller_keeps_everything() {
        // AC-9 / D8 net-zero degrade.
        CommissionSplit split = CommissionPolicy.split(30_000L, 0);

        assertThat(split.commissionMinor()).isZero();
        assertThat(split.sellerNetMinor()).isEqualTo(30_000L);
    }

    @Test
    void full_rate_takes_everything_as_commission() {
        CommissionSplit split = CommissionPolicy.split(30_000L, 10_000);

        assertThat(split.commissionMinor()).isEqualTo(30_000L);
        assertThat(split.sellerNetMinor()).isZero();
    }

    @ParameterizedTest
    @CsvSource({
            // gross, bps, expectedCommission (HALF_UP on gross*bps/10000)
            "1000,  1500, 150",   // exact
            "1005,  1000, 101",   // 100.5 → HALF_UP 101
            "1004,  1000, 100",   // 100.4 → 100
            "333,   1000, 33",    // 33.3 → 33
            "335,   1500, 50",    // 50.25 → 50
            "1,     5000, 1"      // 0.5 → HALF_UP 1
    })
    void rounds_commission_half_up(long gross, int bps, long expectedCommission) {
        CommissionSplit split = CommissionPolicy.split(gross, bps);

        assertThat(split.commissionMinor()).isEqualTo(expectedCommission);
        assertThat(split.sellerNetMinor()).isEqualTo(gross - expectedCommission);
    }

    @Test
    void rejects_rate_out_of_range() {
        assertThatThrownBy(() -> CommissionPolicy.split(1000L, 10_001))
                .isInstanceOf(InvalidCommissionRateException.class);
        assertThatThrownBy(() -> CommissionPolicy.split(1000L, -1))
                .isInstanceOf(InvalidCommissionRateException.class);
    }

    @Test
    void negated_split_flips_every_amount_sign_for_reversal() {
        CommissionSplit split = CommissionPolicy.split(30_000L, 1000);
        CommissionSplit reversed = split.negated();

        assertThat(reversed.grossMinor()).isEqualTo(-30_000L);
        assertThat(reversed.commissionMinor()).isEqualTo(-3_000L);
        assertThat(reversed.sellerNetMinor()).isEqualTo(-27_000L);
        // invariant still holds on the negated split
        assertThat(reversed.commissionMinor() + reversed.sellerNetMinor())
                .isEqualTo(reversed.grossMinor());
    }
}
