package com.example.security.domain.detection;

/**
 * Strategy interface for suspicious-activity rules.
 *
 * <p>Implementations must be stateless and pure (no side effects beyond the
 * {@code ctx}-driven read-through caches they own). Parameters such as
 * thresholds and windows must be injected via configuration — no hard-coded
 * values.</p>
 *
 * <p>Return {@link DetectionResult#NONE} when the rule does not fire for the
 * given context (missing data, unrelated event type, etc.). Never return
 * {@code null}.</p>
 */
public interface SuspiciousActivityRule {

    /**
     * Unique rule code (e.g. {@code VELOCITY}, {@code GEO_ANOMALY}).
     */
    String ruleCode();

    /**
     * Evaluate the rule against the given context.
     *
     * @return detection result with a risk score in [0, 100]; never {@code null}
     */
    DetectionResult evaluate(EvaluationContext ctx);
}
