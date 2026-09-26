package com.example.security.service.domain.detection;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TokenReuseRule — scores {@code auth.token.reuse.detected} events by how often the same
 * account reused a rotated refresh token within the counter window.
 *
 * <p><b>TASK-BE-606 (owner decision 2026-09-26).</b> A single reuse event is
 * {@link RiskLevel#ALERT} ({@link #DEFAULT_SINGLE_SCORE}: recorded + alerted, no lock); the
 * second and later within the window are {@link RiskLevel#AUTO_LOCK}
 * ({@link #DEFAULT_REPEATED_SCORE}). Before BE-606 every event scored 100 and locked the
 * account at once. Why the change: auth-service already revokes the account's whole
 * refresh-token family on the FIRST reuse, so the lock is an additional step — and a single
 * event can be a client race that fell outside auth-service's 30 s replay grace window (a
 * retried request whose response was lost), which must not lock the user out. Repetition
 * within an hour is what separates a persisting attacker from that.
 *
 * <p>The window is the per-tenant per-account Redis counter introduced by TASK-BE-259
 * (key {@code reuse:{tenantId}:{accountId}}, TTL 1h set on the first increment — a fixed
 * window from the first reuse, not a sliding one). Multi-tenant isolation is unchanged:
 * tenantA reuse activity cannot count toward tenantB.
 *
 * <p><b>Counter unavailable ⇒ lock (fail-closed).</b> The counter returns 0 when Redis is down
 * (it never returns 0 after a successful increment). The rule then cannot tell a first reuse
 * from a repeated one and falls back to the pre-BE-606 behaviour — {@link #DEFAULT_REPEATED_SCORE}
 * — rather than silently downgrading every reuse to an alert during an outage.
 */
public class TokenReuseRule implements SuspiciousActivityRule {

    public static final String CODE = "TOKEN_REUSE";

    /** Score of the first reuse in the window — ALERT band (50–79). */
    public static final int DEFAULT_SINGLE_SCORE = 70;
    /** Score from the {@link #DEFAULT_LOCK_THRESHOLD}-th reuse in the window — AUTO_LOCK band (≥ 80). */
    public static final int DEFAULT_REPEATED_SCORE = 100;
    /** How many reuse events in the window lock the account. */
    public static final int DEFAULT_LOCK_THRESHOLD = 2;

    private final TokenReuseCounter counter;
    private final int singleScore;
    private final int repeatedScore;
    private final int lockThreshold;

    public TokenReuseRule(TokenReuseCounter counter) {
        this(counter, DEFAULT_SINGLE_SCORE, DEFAULT_REPEATED_SCORE, DEFAULT_LOCK_THRESHOLD);
    }

    public TokenReuseRule(TokenReuseCounter counter, int singleScore, int repeatedScore, int lockThreshold) {
        if (singleScore < 0 || singleScore > 100) {
            throw new IllegalArgumentException("singleScore must be in [0,100], got " + singleScore);
        }
        if (repeatedScore < 0 || repeatedScore > 100) {
            throw new IllegalArgumentException("repeatedScore must be in [0,100], got " + repeatedScore);
        }
        if (lockThreshold < 1) {
            throw new IllegalArgumentException("lockThreshold must be >= 1, got " + lockThreshold);
        }
        this.counter = counter;
        this.singleScore = singleScore;
        this.repeatedScore = repeatedScore;
        this.lockThreshold = lockThreshold;
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

        boolean counterUnavailable = count <= 0;
        boolean repeated = count >= lockThreshold;
        int score = (counterUnavailable || repeated) ? repeatedScore : singleScore;

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("description", repeated
                ? "Rotated refresh token reused repeatedly within the window"
                : counterUnavailable
                        ? "Rotated refresh token reused — reuse counter unavailable, treated as repeated"
                        : "Rotated refresh token reused — first in the window");
        evidence.put("triggerEventId", ctx.eventId());
        evidence.put("tenantId", tenantId);
        evidence.put("reuseCount", count);
        evidence.put("lockThreshold", lockThreshold);
        return new DetectionResult(CODE, score, evidence);
    }
}
