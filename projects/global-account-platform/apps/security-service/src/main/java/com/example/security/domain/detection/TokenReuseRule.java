package com.example.security.domain.detection;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TokenReuseRule — fires immediately on any {@code auth.token.reuse.detected}
 * event with a fixed score of 100 (AUTO_LOCK, highest priority). auth-service
 * has already determined that a rotated refresh token was reused, which is a
 * confirmed security breach; no threshold logic is required.
 *
 * <p>TASK-BE-259: the rule additionally increments a per-tenant per-account
 * Redis counter (key {@code reuse:{tenantId}:{accountId}}, TTL 1h) for
 * observability and alerting. The counter does <b>not</b> change firing
 * behaviour — every reuse event still produces a score 100 result. It exists
 * solely so dashboards can detect attack patterns (e.g. multiple reuse events
 * for the same tenant/account in a short window) on a per-tenant basis,
 * enforcing the multi-tenant isolation guarantee that tenantA reuse activity
 * cannot influence tenantB detection.</p>
 *
 * <p>The previous global counter scheme ({@code reuse:{accountId}}) was
 * replaced outright; legacy keys expire naturally via TTL.</p>
 */
public class TokenReuseRule implements SuspiciousActivityRule {

    public static final String CODE = "TOKEN_REUSE";
    private static final int FIXED_SCORE = 100;

    private final TokenReuseCounter counter;

    public TokenReuseRule(TokenReuseCounter counter) {
        this.counter = counter;
    }

    @Override
    public String ruleCode() {
        return CODE;
    }

    @Override
    public DetectionResult evaluate(EvaluationContext ctx) {
        if (ctx == null || !ctx.isTokenReuseDetected() || !ctx.hasAccount()) {
            return DetectionResult.NONE;
        }
        // TASK-BE-259: per-tenant Redis counter — events without tenantId never
        // reach this rule (DLQ at the consumer boundary), but if the legacy
        // EvaluationContext constructor is used in tests we fall back to "" so
        // the key is still well-formed and isolated from any named tenant.
        String tenantId = ctx.tenantId() != null ? ctx.tenantId() : "";
        long count = counter.incrementAndGet(tenantId, ctx.accountId());

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("description", "Rotated refresh token was reused — confirmed compromise");
        evidence.put("triggerEventId", ctx.eventId());
        evidence.put("tenantId", tenantId);
        evidence.put("reuseCount", count);
        return new DetectionResult(CODE, FIXED_SCORE, evidence);
    }
}
