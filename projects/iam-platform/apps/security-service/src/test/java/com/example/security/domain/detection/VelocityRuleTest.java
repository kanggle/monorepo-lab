package com.example.security.domain.detection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class VelocityRuleTest {

    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";
    private static final String ACCOUNT_1 = "acc-1";

    @Mock
    private VelocityCounter counter;

    private final DetectionThresholds thresholds = DetectionThresholds.defaults();

    /** tenant-aware context for auth.login.failed. */
    private EvaluationContext failedCtx(String tenantId) {
        return new EvaluationContext(
                tenantId, "evt-1", "auth.login.failed", ACCOUNT_1,
                "1.2.3.***", "fp-1", "KR", Instant.now(), 0);
    }

    /** Legacy context without tenantId — exercises the null-safe tenantId fallback in VelocityRule. */
    private EvaluationContext failedCtxNoTenant() {
        return new EvaluationContext(
                "evt-1", "auth.login.failed", ACCOUNT_1,
                "1.2.3.***", "fp-1", "KR", Instant.now(), 0);
    }

    @Test
    @DisplayName("Just below threshold does not fire (9/10)")
    void justBelowThreshold() {
        when(counter.incrementAndGet(TENANT_A, ACCOUNT_1, 3600)).thenReturn(9L);
        DetectionResult r = new VelocityRule(counter, thresholds).evaluate(failedCtx(TENANT_A));
        assertThat(r.fired()).isFalse();
    }

    @Test
    @DisplayName("At threshold fires with score = 80 (AUTO_LOCK floor)")
    void atThreshold() {
        when(counter.incrementAndGet(TENANT_A, ACCOUNT_1, 3600)).thenReturn(10L);
        DetectionResult r = new VelocityRule(counter, thresholds).evaluate(failedCtx(TENANT_A));
        assertThat(r.fired()).isTrue();
        assertThat(r.riskScore()).isEqualTo(80);
        assertThat(r.ruleCode()).isEqualTo("VELOCITY");
        assertThat(RiskLevel.fromScore(r.riskScore())).isEqualTo(RiskLevel.AUTO_LOCK);
    }

    @Test
    @DisplayName("12 failures / 10 threshold → score 96 per spec UC-10")
    void uc10Example() {
        when(counter.incrementAndGet(TENANT_A, ACCOUNT_1, 3600)).thenReturn(12L);
        DetectionResult r = new VelocityRule(counter, thresholds).evaluate(failedCtx(TENANT_A));
        assertThat(r.riskScore()).isEqualTo(96);
    }

    @Test
    @DisplayName("Score is capped at 100 for sustained attacks")
    void capAt100() {
        when(counter.incrementAndGet(TENANT_A, ACCOUNT_1, 3600)).thenReturn(1000L);
        DetectionResult r = new VelocityRule(counter, thresholds).evaluate(failedCtx(TENANT_A));
        assertThat(r.riskScore()).isEqualTo(100);
    }

    @Test
    @DisplayName("Succeeded events are ignored (no increment, no score)")
    void ignoresNonFailed() {
        EvaluationContext ok = new EvaluationContext(
                TENANT_A, "evt-1", "auth.login.succeeded", ACCOUNT_1,
                "1.2.3.***", "fp-1", "KR", Instant.now(), 0);
        DetectionResult r = new VelocityRule(counter, thresholds).evaluate(ok);
        assertThat(r.fired()).isFalse();
        verifyNoInteractions(counter);
    }

    @Test
    @DisplayName("Events without accountId are skipped")
    void skipsUnknownAccount() {
        EvaluationContext ctx = new EvaluationContext(
                TENANT_A, "evt-1", "auth.login.failed", null,
                "1.2.3.***", "fp-1", "KR", Instant.now(), 0);
        DetectionResult r = new VelocityRule(counter, thresholds).evaluate(ctx);
        assertThat(r.fired()).isFalse();
        verifyNoInteractions(counter);
    }

    @Test
    @DisplayName("Threshold change alters firing behaviour (config-driven)")
    void configDrivenThreshold() {
        DetectionThresholds tight = new DetectionThresholds(3, 3600, 80, 900, 85, 15, 50, true);
        when(counter.incrementAndGet(TENANT_A, ACCOUNT_1, 3600)).thenReturn(3L);
        DetectionResult r = new VelocityRule(counter, tight).evaluate(failedCtx(TENANT_A));
        assertThat(r.fired()).isTrue();
        assertThat(r.riskScore()).isEqualTo(80);
    }

    @Test
    @DisplayName("Evidence includes tenantId for auditability")
    void evidenceContainsTenantId() {
        when(counter.incrementAndGet(TENANT_A, ACCOUNT_1, 3600)).thenReturn(10L);
        DetectionResult r = new VelocityRule(counter, thresholds).evaluate(failedCtx(TENANT_A));
        assertThat(r.fired()).isTrue();
        assertThat(r.evidence()).containsKey("tenantId");
        assertThat(r.evidence().get("tenantId")).isEqualTo(TENANT_A);
    }

    // ── TASK-BE-248 Phase 1: Cross-Tenant Isolation Tests ─────────────────────

    @Test
    @DisplayName("[cross-tenant] tenantA 50회 실패가 tenantB 동일 계정 임계치에 영향 없음")
    void crossTenantIsolation_tenantACounterDoesNotAffectTenantB() {
        // Given: tenantA has accumulated 50 failures → fires
        when(counter.incrementAndGet(TENANT_A, ACCOUNT_1, 3600)).thenReturn(50L);
        // tenantB counter for the same accountId stays at 1 (no accumulation)
        when(counter.incrementAndGet(TENANT_B, ACCOUNT_1, 3600)).thenReturn(1L);

        VelocityRule rule = new VelocityRule(counter, thresholds);

        // tenantA fires
        DetectionResult resultA = rule.evaluate(failedCtx(TENANT_A));
        assertThat(resultA.fired()).isTrue();
        assertThat(resultA.riskScore()).isEqualTo(100);

        // tenantB does NOT fire — its counter is independent
        DetectionResult resultB = rule.evaluate(failedCtx(TENANT_B));
        assertThat(resultB.fired()).isFalse();
    }

    @Test
    @DisplayName("[cross-tenant] 두 테넌트의 카운터는 독립적인 Redis key 사용")
    void crossTenantIsolation_separateCounterKeys() {
        when(counter.incrementAndGet(TENANT_A, ACCOUNT_1, 3600)).thenReturn(10L);
        when(counter.incrementAndGet(TENANT_B, ACCOUNT_1, 3600)).thenReturn(3L);

        VelocityRule rule = new VelocityRule(counter, thresholds);
        rule.evaluate(failedCtx(TENANT_A));
        rule.evaluate(failedCtx(TENANT_B));

        // Verify each tenant's counter was incremented separately — the mock verifies
        // that tenantId is passed as a distinct argument per call.
        verify(counter).incrementAndGet(TENANT_A, ACCOUNT_1, 3600);
        verify(counter).incrementAndGet(TENANT_B, ACCOUNT_1, 3600);
        verifyNoMoreInteractions(counter);
    }

    @Test
    @DisplayName("[cross-tenant] null tenantId context → 빈 문자열 tenantId 사용 (안전 fallback)")
    void nullTenantIdContext_fallsBackToEmptyString() {
        // The legacy EvaluationContext constructor sets tenantId = null.
        // VelocityRule falls back to "" to avoid NullPointerException in key construction.
        when(counter.incrementAndGet("", ACCOUNT_1, 3600)).thenReturn(10L);
        DetectionResult r = new VelocityRule(counter, thresholds).evaluate(failedCtxNoTenant());
        assertThat(r.fired()).isTrue();
        // Verify the fallback key ("") was used, not null
        verify(counter).incrementAndGet("", ACCOUNT_1, 3600);
    }
}
