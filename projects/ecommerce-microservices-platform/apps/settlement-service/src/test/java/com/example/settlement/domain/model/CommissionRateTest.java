package com.example.settlement.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommissionRateTest {

    @Test
    void accepts_bounds_inclusive() {
        assertThat(new CommissionRate(0, CommissionRate.Source.PLATFORM_DEFAULT).rateBps()).isZero();
        assertThat(new CommissionRate(10_000, CommissionRate.Source.SELLER_OVERRIDE).rateBps())
                .isEqualTo(10_000);
    }

    @Test
    void rejects_out_of_range() {
        assertThatThrownBy(() -> new CommissionRate(-1, CommissionRate.Source.PLATFORM_DEFAULT))
                .isInstanceOf(InvalidCommissionRateException.class);
        assertThatThrownBy(() -> new CommissionRate(10_001, CommissionRate.Source.SELLER_OVERRIDE))
                .isInstanceOf(InvalidCommissionRateException.class);
    }

    @Test
    void factory_methods_carry_source() {
        assertThat(CommissionRate.sellerOverride(500).source())
                .isEqualTo(CommissionRate.Source.SELLER_OVERRIDE);
        assertThat(CommissionRate.platformDefault(0).source())
                .isEqualTo(CommissionRate.Source.PLATFORM_DEFAULT);
    }

    @Test
    void isValidBps_matches_bounds() {
        assertThat(CommissionRate.isValidBps(0)).isTrue();
        assertThat(CommissionRate.isValidBps(10_000)).isTrue();
        assertThat(CommissionRate.isValidBps(-1)).isFalse();
        assertThat(CommissionRate.isValidBps(10_001)).isFalse();
    }
}
