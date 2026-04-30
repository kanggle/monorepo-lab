package com.example.security.domain.detection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RiskScoreAggregatorTest {

    @Test
    @DisplayName("Empty or all-NONE results aggregate to NONE")
    void emptyOrNone() {
        assertThat(RiskScoreAggregator.aggregate(List.of()).anyFired()).isFalse();
        assertThat(RiskScoreAggregator.aggregate(List.of(DetectionResult.NONE, DetectionResult.NONE)).anyFired()).isFalse();
    }

    @Test
    @DisplayName("Picks highest-scoring rule as winner (max aggregation)")
    void maxWins() {
        DetectionResult device = new DetectionResult("DEVICE_CHANGE", 50, Map.of());
        DetectionResult geo = new DetectionResult("GEO_ANOMALY", 92, Map.of());
        DetectionResult velocity = new DetectionResult("VELOCITY", 80, Map.of());

        RiskScoreAggregator.Aggregated agg = RiskScoreAggregator.aggregate(List.of(device, geo, velocity));
        assertThat(agg.winner().ruleCode()).isEqualTo("GEO_ANOMALY");
        assertThat(agg.winner().riskScore()).isEqualTo(92);
        assertThat(agg.level()).isEqualTo(RiskLevel.AUTO_LOCK);
        assertThat(agg.firedResults()).hasSize(3);
    }

    @Test
    @DisplayName("First-wins tie-breaker on equal scores")
    void ties() {
        DetectionResult a = new DetectionResult("A", 70, Map.of());
        DetectionResult b = new DetectionResult("B", 70, Map.of());
        RiskScoreAggregator.Aggregated agg = RiskScoreAggregator.aggregate(List.of(a, b));
        assertThat(agg.winner().ruleCode()).isEqualTo("A");
        assertThat(agg.level()).isEqualTo(RiskLevel.ALERT);
    }
}
