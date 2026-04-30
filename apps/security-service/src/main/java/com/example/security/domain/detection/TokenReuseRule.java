package com.example.security.domain.detection;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TokenReuseRule — fires immediately on any {@code auth.token.reuse.detected}
 * event with a fixed score of 100 (AUTO_LOCK, highest priority). auth-service
 * has already determined that a rotated refresh token was reused, which is a
 * confirmed security breach; no threshold logic is required.
 */
public class TokenReuseRule implements SuspiciousActivityRule {

    public static final String CODE = "TOKEN_REUSE";

    @Override
    public String ruleCode() {
        return CODE;
    }

    @Override
    public DetectionResult evaluate(EvaluationContext ctx) {
        if (ctx == null || !ctx.isTokenReuseDetected() || !ctx.hasAccount()) {
            return DetectionResult.NONE;
        }
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("description", "Rotated refresh token was reused — confirmed compromise");
        evidence.put("triggerEventId", ctx.eventId());
        return new DetectionResult(CODE, 100, evidence);
    }
}
