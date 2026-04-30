package com.example.membership.domain.plan;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanLevelTest {

    @Test
    void freeRankLowerThanFanClub() {
        assertThat(PlanLevel.FREE.getRank()).isEqualTo(0);
        assertThat(PlanLevel.FAN_CLUB.getRank()).isEqualTo(1);
    }

    @Test
    void meetsOrExceeds_sameLevel_true() {
        assertThat(PlanLevel.FAN_CLUB.meetsOrExceeds(PlanLevel.FAN_CLUB)).isTrue();
    }

    @Test
    void meetsOrExceeds_higherLevel_true() {
        assertThat(PlanLevel.FAN_CLUB.meetsOrExceeds(PlanLevel.FREE)).isTrue();
    }

    @Test
    void meetsOrExceeds_lowerLevel_false() {
        assertThat(PlanLevel.FREE.meetsOrExceeds(PlanLevel.FAN_CLUB)).isFalse();
    }

    @Test
    void parse_unknown_throws() {
        assertThatThrownBy(() -> PlanLevel.parse("VIP"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parse_null_throws() {
        assertThatThrownBy(() -> PlanLevel.parse(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
