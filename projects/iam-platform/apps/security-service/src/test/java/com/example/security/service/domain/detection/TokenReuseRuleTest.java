package com.example.security.service.domain.detection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class TokenReuseRuleTest {

    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";
    private static final String ACCOUNT_1 = "acc-1";

    @Mock
    private TokenReuseCounter counter;

    /** Tenant-aware reuse context (TASK-BE-259). */
    private EvaluationContext reuseCtx(String tenantId, String eventId) {
        return new EvaluationContext(
                tenantId, eventId, "auth.token.reuse.detected", ACCOUNT_1,
                "1.2.3.***", "fp-1", null, Instant.now(), null);
    }

    /** Legacy context without tenantId — exercises the null-safe fallback in TokenReuseRule. */
    private EvaluationContext reuseCtxNoTenant(String eventId) {
        return new EvaluationContext(
                eventId, "auth.token.reuse.detected", ACCOUNT_1,
                "1.2.3.***", "fp-1", null, Instant.now(), null);
    }

    // ── TASK-BE-606: 1 reuse = ALERT, 2+ within the counter window = AUTO_LOCK ──────────

    @Test
    @DisplayName("[BE-606] 1시간 창의 첫 재사용 → score 70 = ALERT (잠금 없음)")
    void firstReuse_alertsWithoutLock() {
        when(counter.incrementAndGet(TENANT_A, ACCOUNT_1)).thenReturn(1L);

        TokenReuseRule rule = new TokenReuseRule(counter);
        DetectionResult r = rule.evaluate(reuseCtx(TENANT_A, "evt-1"));

        assertThat(r.fired()).isTrue();
        assertThat(r.riskScore()).isEqualTo(70);
        assertThat(r.ruleCode()).isEqualTo("TOKEN_REUSE");
        assertThat(RiskLevel.fromScore(r.riskScore())).isEqualTo(RiskLevel.ALERT);
    }

    @Test
    @DisplayName("[BE-606] 같은 계정 1시간 안 두 번째 재사용 → score 100 = AUTO_LOCK")
    void secondReuseWithinWindow_autoLocks() {
        when(counter.incrementAndGet(TENANT_A, ACCOUNT_1)).thenReturn(1L, 2L);

        TokenReuseRule rule = new TokenReuseRule(counter);
        DetectionResult first = rule.evaluate(reuseCtx(TENANT_A, "evt-1"));
        DetectionResult second = rule.evaluate(reuseCtx(TENANT_A, "evt-2"));

        assertThat(RiskLevel.fromScore(first.riskScore())).isEqualTo(RiskLevel.ALERT);
        assertThat(second.riskScore()).isEqualTo(100);
        assertThat(RiskLevel.fromScore(second.riskScore())).isEqualTo(RiskLevel.AUTO_LOCK);
    }

    @Test
    @DisplayName("[BE-606] 창 안 세 번째 이후도 AUTO_LOCK")
    void laterReuseWithinWindow_autoLocks() {
        when(counter.incrementAndGet(TENANT_A, ACCOUNT_1)).thenReturn(5L);

        DetectionResult r = new TokenReuseRule(counter).evaluate(reuseCtx(TENANT_A, "evt-5"));

        assertThat(RiskLevel.fromScore(r.riskScore())).isEqualTo(RiskLevel.AUTO_LOCK);
    }

    @Test
    @DisplayName("[BE-606] 설정값(single/repeated/threshold)을 따른다 — threshold 3 이면 2번째도 ALERT")
    void configuredThresholdIsHonoured() {
        when(counter.incrementAndGet(TENANT_A, ACCOUNT_1)).thenReturn(2L, 3L);

        TokenReuseRule rule = new TokenReuseRule(counter, 60, 90, 3);

        assertThat(rule.evaluate(reuseCtx(TENANT_A, "evt-2")).riskScore()).isEqualTo(60);
        assertThat(rule.evaluate(reuseCtx(TENANT_A, "evt-3")).riskScore()).isEqualTo(90);
    }

    @Test
    @DisplayName("[BE-606] 잘못된 설정 → 생성 거부")
    void invalidConfigurationRejected() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new TokenReuseRule(counter, 101, 100, 2))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new TokenReuseRule(counter, 70, 100, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Other event types do not fire (and do not increment the counter)")
    void otherEventsIgnored() {
        TokenReuseRule rule = new TokenReuseRule(counter);
        EvaluationContext ctx = new EvaluationContext(
                TENANT_A, "evt-1", "auth.login.succeeded", ACCOUNT_1,
                "1.2.3.***", "fp-1", "KR", Instant.now(), null);

        DetectionResult r = rule.evaluate(ctx);

        assertThat(r.fired()).isFalse();
        verifyNoInteractions(counter);
    }

    @Test
    @DisplayName("Missing accountId → skip (do not lock unknown, no counter increment)")
    void missingAccount() {
        TokenReuseRule rule = new TokenReuseRule(counter);
        EvaluationContext ctx = new EvaluationContext(
                TENANT_A, "evt-1", "auth.token.reuse.detected", null,
                "1.2.3.***", "fp-1", null, Instant.now(), null);

        DetectionResult r = rule.evaluate(ctx);

        assertThat(r.fired()).isFalse();
        verifyNoInteractions(counter);
    }

    @Test
    @DisplayName("Evidence carries tenantId and reuseCount for auditability")
    void evidenceContainsTenantIdAndCount() {
        when(counter.incrementAndGet(TENANT_A, ACCOUNT_1)).thenReturn(3L);

        TokenReuseRule rule = new TokenReuseRule(counter);
        DetectionResult r = rule.evaluate(reuseCtx(TENANT_A, "evt-1"));

        assertThat(r.evidence()).containsKey("tenantId");
        assertThat(r.evidence().get("tenantId")).isEqualTo(TENANT_A);
        assertThat(r.evidence()).containsKey("reuseCount");
        assertThat(r.evidence().get("reuseCount")).isEqualTo(3L);
        assertThat(r.evidence()).containsKey("triggerEventId");
        assertThat(r.evidence().get("triggerEventId")).isEqualTo("evt-1");
    }

    // ── TASK-BE-259: Cross-Tenant Isolation Tests ─────────────────────────────

    @Test
    @DisplayName("[cross-tenant] tenantA 50회 reuse 가 tenantB 동일 account 의 카운터에 영향 없음")
    void crossTenantIsolation_tenantACounterDoesNotAffectTenantB() {
        // Simulate 50 reuse events for tenantA's account-1 — counter accumulates 1..50
        TokenReuseRule rule = new TokenReuseRule(counter);
        for (int i = 1; i <= 50; i++) {
            when(counter.incrementAndGet(TENANT_A, ACCOUNT_1)).thenReturn((long) i);
            DetectionResult r = rule.evaluate(reuseCtx(TENANT_A, "evt-A-" + i));
            // TASK-BE-606: the first alerts (70), every later one locks (100)
            assertThat(r.riskScore()).isEqualTo(i == 1 ? 70 : 100);
        }

        // Now a reuse event for tenantB hits the SAME accountId — its counter
        // is independent (starts at 1, NOT 51). TASK-BE-606 makes the isolation
        // visible in the SCORE: tenantA's 50 events do not make tenantB's first
        // one a lock — it is an ALERT (70).
        when(counter.incrementAndGet(TENANT_B, ACCOUNT_1)).thenReturn(1L);
        DetectionResult resultB = rule.evaluate(reuseCtx(TENANT_B, "evt-B-1"));

        assertThat(resultB.fired()).isTrue();
        assertThat(resultB.riskScore()).isEqualTo(70);
        assertThat(resultB.evidence().get("reuseCount")).isEqualTo(1L);
        assertThat(resultB.evidence().get("tenantId")).isEqualTo(TENANT_B);

        // Mock verification proves the rule passed tenantId distinctly per call —
        // i.e. it never reused tenantA's key for tenantB.
        verify(counter, org.mockito.Mockito.times(50)).incrementAndGet(TENANT_A, ACCOUNT_1);
        verify(counter).incrementAndGet(TENANT_B, ACCOUNT_1);
        verifyNoMoreInteractions(counter);
    }

    @Test
    @DisplayName("[cross-tenant] 두 테넌트는 독립적인 Redis key 로 카운트됨")
    void crossTenantIsolation_separateCounterKeys() {
        when(counter.incrementAndGet(TENANT_A, ACCOUNT_1)).thenReturn(7L);
        when(counter.incrementAndGet(TENANT_B, ACCOUNT_1)).thenReturn(2L);

        TokenReuseRule rule = new TokenReuseRule(counter);
        DetectionResult resultA = rule.evaluate(reuseCtx(TENANT_A, "evt-A"));
        DetectionResult resultB = rule.evaluate(reuseCtx(TENANT_B, "evt-B"));

        assertThat(resultA.evidence().get("reuseCount")).isEqualTo(7L);
        assertThat(resultB.evidence().get("reuseCount")).isEqualTo(2L);

        verify(counter).incrementAndGet(TENANT_A, ACCOUNT_1);
        verify(counter).incrementAndGet(TENANT_B, ACCOUNT_1);
        verifyNoMoreInteractions(counter);
    }

    @Test
    @DisplayName("[cross-tenant] null tenantId context → 빈 문자열 fallback (안전)")
    void nullTenantIdContext_fallsBackToEmptyString() {
        // The legacy EvaluationContext constructor sets tenantId = null. The
        // rule falls back to "" to avoid NullPointerException in key construction
        // and to keep the counter isolated from any named tenant.
        when(counter.incrementAndGet("", ACCOUNT_1)).thenReturn(1L);

        TokenReuseRule rule = new TokenReuseRule(counter);
        DetectionResult r = rule.evaluate(reuseCtxNoTenant("evt-legacy"));

        assertThat(r.fired()).isTrue();
        assertThat(r.evidence().get("tenantId")).isEqualTo("");
        verify(counter).incrementAndGet("", ACCOUNT_1);
    }

    @Test
    @DisplayName("Redis 장애 (counter=0) → 첫 재사용인지 알 수 없음 → score 100 (fail-closed, BE-606)")
    void redisOutage_stillFiresAtFullScore() {
        // RedisTokenReuseCounter returns 0 on any Redis error. TASK-BE-606: the count
        // now decides ALERT vs AUTO_LOCK; an unknown count falls back to the
        // pre-BE-606 behaviour (lock) instead of downgrading every reuse to an alert.
        when(counter.incrementAndGet(TENANT_A, ACCOUNT_1)).thenReturn(0L);

        TokenReuseRule rule = new TokenReuseRule(counter);
        DetectionResult r = rule.evaluate(reuseCtx(TENANT_A, "evt-redis-down"));

        assertThat(r.fired()).isTrue();
        assertThat(r.riskScore()).isEqualTo(100);
        assertThat(r.evidence().get("reuseCount")).isEqualTo(0L);
    }
}
