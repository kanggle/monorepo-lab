package com.wms.admin.infra.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LikePatternsTest {

    @Test
    void absentFilter_staysNull_soTheIsNullGuardShortCircuits() {
        assertThat(LikePatterns.prefix(null)).isNull();
        assertThat(LikePatterns.lowerContains(null)).isNull();
    }

    @Test
    void prefix_appendsPercent_withoutEscaping() {
        assertThat(LikePatterns.prefix("inventory.low_stock")).isEqualTo("inventory.low_stock%");
        assertThat(LikePatterns.prefix("")).isEqualTo("%");
    }

    @Test
    void lowerContains_wrapsAndLowerCases() {
        assertThat(LikePatterns.lowerContains("AbC")).isEqualTo("%abc%");
        assertThat(LikePatterns.lowerContains("")).isEqualTo("%%");
    }
}
