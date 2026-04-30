package com.example.security.domain.detection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Aggregates multiple {@link DetectionResult}s into a single final decision.
 *
 * <p>Spec: {@code finalScore = max(rule1.score, rule2.score, ...)}. The rule
 * whose score equals the max wins the {@code ruleCode}. Ties are broken by
 * evaluation order (first wins).</p>
 */
public final class RiskScoreAggregator {

    private RiskScoreAggregator() {
    }

    public static Aggregated aggregate(Collection<DetectionResult> results) {
        if (results == null || results.isEmpty()) {
            return new Aggregated(DetectionResult.NONE, List.of());
        }
        List<DetectionResult> fired = new ArrayList<>();
        DetectionResult winner = DetectionResult.NONE;
        for (DetectionResult r : results) {
            if (r == null || !r.fired()) {
                continue;
            }
            fired.add(r);
            if (r.riskScore() > winner.riskScore()) {
                winner = r;
            }
        }
        return new Aggregated(winner, Collections.unmodifiableList(fired));
    }

    public record Aggregated(DetectionResult winner, List<DetectionResult> firedResults) {
        public RiskLevel level() {
            return RiskLevel.fromScore(winner.riskScore());
        }

        public boolean anyFired() {
            return winner.fired();
        }
    }
}
