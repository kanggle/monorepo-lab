package com.example.security.domain.detection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RiskLevelTest {

    @Test
    @DisplayName("Maps score to action threshold boundaries per spec")
    void fromScore() {
        assertThat(RiskLevel.fromScore(0)).isEqualTo(RiskLevel.NONE);
        assertThat(RiskLevel.fromScore(49)).isEqualTo(RiskLevel.NONE);
        assertThat(RiskLevel.fromScore(50)).isEqualTo(RiskLevel.ALERT);
        assertThat(RiskLevel.fromScore(79)).isEqualTo(RiskLevel.ALERT);
        assertThat(RiskLevel.fromScore(80)).isEqualTo(RiskLevel.AUTO_LOCK);
        assertThat(RiskLevel.fromScore(100)).isEqualTo(RiskLevel.AUTO_LOCK);
    }

    @Test
    @DisplayName("Rejects out-of-range scores")
    void rejectsOutOfRange() {
        assertThatThrownBy(() -> RiskLevel.fromScore(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RiskLevel.fromScore(101)).isInstanceOf(IllegalArgumentException.class);
    }
}
