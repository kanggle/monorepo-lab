package com.example.security.domain.detection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VelocityRuleTest {

    @Mock
    private VelocityCounter counter;

    private final DetectionThresholds thresholds = DetectionThresholds.defaults();

    private EvaluationContext failedCtx() {
        return new EvaluationContext(
                "evt-1", "auth.login.failed", "acc-1",
                "1.2.3.***", "fp-1", "KR", Instant.now(), 0);
    }

    @Test
    @DisplayName("Just below threshold does not fire (9/10)")
    void justBelowThreshold() {
        when(counter.incrementAndGet("acc-1", 3600)).thenReturn(9L);
        DetectionResult r = new VelocityRule(counter, thresholds).evaluate(failedCtx());
        assertThat(r.fired()).isFalse();
    }

    @Test
    @DisplayName("At threshold fires with score = 80 (AUTO_LOCK floor)")
    void atThreshold() {
        when(counter.incrementAndGet("acc-1", 3600)).thenReturn(10L);
        DetectionResult r = new VelocityRule(counter, thresholds).evaluate(failedCtx());
        assertThat(r.fired()).isTrue();
        assertThat(r.riskScore()).isEqualTo(80);
        assertThat(r.ruleCode()).isEqualTo("VELOCITY");
        assertThat(RiskLevel.fromScore(r.riskScore())).isEqualTo(RiskLevel.AUTO_LOCK);
    }

    @Test
    @DisplayName("12 failures / 10 threshold → score 96 per spec UC-10")
    void uc10Example() {
        when(counter.incrementAndGet("acc-1", 3600)).thenReturn(12L);
        DetectionResult r = new VelocityRule(counter, thresholds).evaluate(failedCtx());
        assertThat(r.riskScore()).isEqualTo(96);
    }

    @Test
    @DisplayName("Score is capped at 100 for sustained attacks")
    void capAt100() {
        when(counter.incrementAndGet("acc-1", 3600)).thenReturn(1000L);
        DetectionResult r = new VelocityRule(counter, thresholds).evaluate(failedCtx());
        assertThat(r.riskScore()).isEqualTo(100);
    }

    @Test
    @DisplayName("Succeeded events are ignored (no increment, no score)")
    void ignoresNonFailed() {
        EvaluationContext ok = new EvaluationContext(
                "evt-1", "auth.login.succeeded", "acc-1",
                "1.2.3.***", "fp-1", "KR", Instant.now(), 0);
        DetectionResult r = new VelocityRule(counter, thresholds).evaluate(ok);
        assertThat(r.fired()).isFalse();
        verifyNoInteractions(counter);
    }

    @Test
    @DisplayName("Events without accountId are skipped")
    void skipsUnknownAccount() {
        EvaluationContext ctx = new EvaluationContext(
                "evt-1", "auth.login.failed", null,
                "1.2.3.***", "fp-1", "KR", Instant.now(), 0);
        DetectionResult r = new VelocityRule(counter, thresholds).evaluate(ctx);
        assertThat(r.fired()).isFalse();
        verifyNoInteractions(counter);
    }

    @Test
    @DisplayName("Threshold change alters firing behaviour (config-driven)")
    void configDrivenThreshold() {
        DetectionThresholds tight = new DetectionThresholds(3, 3600, 80, 900, 85, 15, 50, true);
        when(counter.incrementAndGet("acc-1", 3600)).thenReturn(3L);
        DetectionResult r = new VelocityRule(counter, tight).evaluate(failedCtx());
        assertThat(r.fired()).isTrue();
        assertThat(r.riskScore()).isEqualTo(80);
    }
}
