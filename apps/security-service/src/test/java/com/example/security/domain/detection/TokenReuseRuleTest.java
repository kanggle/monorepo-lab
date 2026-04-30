package com.example.security.domain.detection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TokenReuseRuleTest {

    @Test
    @DisplayName("Any token.reuse.detected event fires at score 100 (AUTO_LOCK)")
    void fires() {
        TokenReuseRule rule = new TokenReuseRule();
        EvaluationContext ctx = new EvaluationContext("evt-1", "auth.token.reuse.detected",
                "acc-1", "1.2.3.***", "fp-1", null, Instant.now(), null);
        DetectionResult r = rule.evaluate(ctx);
        assertThat(r.fired()).isTrue();
        assertThat(r.riskScore()).isEqualTo(100);
        assertThat(r.ruleCode()).isEqualTo("TOKEN_REUSE");
        assertThat(RiskLevel.fromScore(r.riskScore())).isEqualTo(RiskLevel.AUTO_LOCK);
    }

    @Test
    @DisplayName("Other event types do not fire")
    void otherEventsIgnored() {
        TokenReuseRule rule = new TokenReuseRule();
        EvaluationContext ctx = new EvaluationContext("evt-1", "auth.login.succeeded",
                "acc-1", "1.2.3.***", "fp-1", "KR", Instant.now(), null);
        assertThat(rule.evaluate(ctx).fired()).isFalse();
    }

    @Test
    @DisplayName("Missing accountId → skip (do not lock unknown)")
    void missingAccount() {
        TokenReuseRule rule = new TokenReuseRule();
        EvaluationContext ctx = new EvaluationContext("evt-1", "auth.token.reuse.detected",
                null, "1.2.3.***", "fp-1", null, Instant.now(), null);
        assertThat(rule.evaluate(ctx).fired()).isFalse();
    }
}
