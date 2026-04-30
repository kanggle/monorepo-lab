package com.example.security.domain.detection;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * VelocityRule — fires when the per-account failed-login count in the rolling
 * window exceeds {@link DetectionThresholds#velocityThreshold()}.
 *
 * <p>Spec: {@code riskScore = min(100, (failCount / threshold) * 80)} —
 * threshold-scaled so that reaching the threshold yields 80 (AUTO_LOCK floor)
 * and sustained attacks reach 100.</p>
 *
 * <p>Increments the counter only on {@code auth.login.failed} events; other
 * event types are ignored.</p>
 */
public class VelocityRule implements SuspiciousActivityRule {

    public static final String CODE = "VELOCITY";

    private final VelocityCounter counter;
    private final DetectionThresholds thresholds;

    public VelocityRule(VelocityCounter counter, DetectionThresholds thresholds) {
        this.counter = counter;
        this.thresholds = thresholds;
    }

    @Override
    public String ruleCode() {
        return CODE;
    }

    @Override
    public DetectionResult evaluate(EvaluationContext ctx) {
        if (ctx == null || !ctx.isLoginFailed() || !ctx.hasAccount()) {
            return DetectionResult.NONE;
        }
        long count = counter.incrementAndGet(ctx.accountId(), thresholds.velocityWindowSeconds());
        if (count < thresholds.velocityThreshold()) {
            return DetectionResult.NONE;
        }
        int score = (int) Math.min(100L,
                (count * (long) thresholds.velocityScoreWeight()) / thresholds.velocityThreshold());
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("description", "Repeated login failures within window");
        evidence.put("failCount", count);
        evidence.put("threshold", thresholds.velocityThreshold());
        evidence.put("windowSeconds", thresholds.velocityWindowSeconds());
        return new DetectionResult(CODE, score, evidence);
    }
}
