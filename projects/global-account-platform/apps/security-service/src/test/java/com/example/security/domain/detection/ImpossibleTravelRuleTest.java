package com.example.security.domain.detection;

import com.example.security.domain.Tenants;
import com.example.security.domain.history.LoginHistoryEntry;
import com.example.security.domain.history.LoginOutcome;
import com.example.security.domain.repository.LoginHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImpossibleTravelRuleTest {

    @Mock LoginHistoryRepository loginHistoryRepository;

    private final DetectionThresholds thresholds = DetectionThresholds.defaults();

    private EvaluationContext succeededCtx(String geoCountry, Instant at) {
        return new EvaluationContext(Tenants.DEFAULT_TENANT_ID,
                "evt-1", "auth.login.succeeded", "acc-1",
                "1.2.3.***", "fp-1", geoCountry, at, null);
    }

    private LoginHistoryEntry previousLogin(String geoCountry, Instant at) {
        return new LoginHistoryEntry(Tenants.DEFAULT_TENANT_ID,
                "evt-0", "acc-1", LoginOutcome.SUCCESS,
                "1.2.3.***", "Chrome", "fp-1", geoCountry, at);
    }

    @Test
    @DisplayName("Same country → no alert")
    void sameCountryDoesNotFire() {
        Instant now = Instant.parse("2026-04-18T10:00:00Z");
        Instant prev = Instant.parse("2026-04-18T09:30:00Z");

        when(loginHistoryRepository.findLatestSuccessByAccountId(Tenants.DEFAULT_TENANT_ID, "acc-1"))
                .thenReturn(Optional.of(previousLogin("KR", prev)));

        DetectionResult r = new ImpossibleTravelRule(loginHistoryRepository, thresholds)
                .evaluate(succeededCtx("KR", now));

        assertThat(r.fired()).isFalse();
    }

    @Test
    @DisplayName("Different country within time window → ALERT")
    void differentCountryWithinWindowFires() {
        Instant now = Instant.parse("2026-04-18T10:00:00Z");
        Instant prev = Instant.parse("2026-04-18T09:30:00Z"); // 30 min ago, within 1h window

        when(loginHistoryRepository.findLatestSuccessByAccountId(Tenants.DEFAULT_TENANT_ID, "acc-1"))
                .thenReturn(Optional.of(previousLogin("KR", prev)));

        DetectionResult r = new ImpossibleTravelRule(loginHistoryRepository, thresholds)
                .evaluate(succeededCtx("US", now));

        assertThat(r.fired()).isTrue();
        assertThat(r.riskScore()).isEqualTo(70);
        assertThat(r.ruleCode()).isEqualTo("IMPOSSIBLE_TRAVEL");
        assertThat(RiskLevel.fromScore(r.riskScore())).isEqualTo(RiskLevel.ALERT);
        assertThat(r.evidence()).containsEntry("previousCountry", "KR");
        assertThat(r.evidence()).containsEntry("currentCountry", "US");
    }

    @Test
    @DisplayName("Different country outside time window → no alert")
    void differentCountryOutsideWindowDoesNotFire() {
        Instant now = Instant.parse("2026-04-18T12:00:00Z");
        Instant prev = Instant.parse("2026-04-18T10:00:00Z"); // 2 hours ago, outside 1h window

        when(loginHistoryRepository.findLatestSuccessByAccountId(Tenants.DEFAULT_TENANT_ID, "acc-1"))
                .thenReturn(Optional.of(previousLogin("KR", prev)));

        DetectionResult r = new ImpossibleTravelRule(loginHistoryRepository, thresholds)
                .evaluate(succeededCtx("US", now));

        assertThat(r.fired()).isFalse();
    }

    @Test
    @DisplayName("No previous login → no alert")
    void noPreviousLoginDoesNotFire() {
        when(loginHistoryRepository.findLatestSuccessByAccountId(Tenants.DEFAULT_TENANT_ID, "acc-1"))
                .thenReturn(Optional.empty());

        DetectionResult r = new ImpossibleTravelRule(loginHistoryRepository, thresholds)
                .evaluate(succeededCtx("US", Instant.now()));

        assertThat(r.fired()).isFalse();
    }

    @Test
    @DisplayName("Null geoCountry on current login → no alert")
    void nullCurrentCountryDoesNotFire() {
        DetectionResult r = new ImpossibleTravelRule(loginHistoryRepository, thresholds)
                .evaluate(succeededCtx(null, Instant.now()));

        assertThat(r.fired()).isFalse();
        verifyNoInteractions(loginHistoryRepository);
    }

    @Test
    @DisplayName("Null geoCountry on previous login → no alert")
    void nullPreviousCountryDoesNotFire() {
        when(loginHistoryRepository.findLatestSuccessByAccountId(Tenants.DEFAULT_TENANT_ID, "acc-1"))
                .thenReturn(Optional.of(previousLogin(null, Instant.now().minusSeconds(600))));

        DetectionResult r = new ImpossibleTravelRule(loginHistoryRepository, thresholds)
                .evaluate(succeededCtx("US", Instant.now()));

        assertThat(r.fired()).isFalse();
    }

    @Test
    @DisplayName("Non-succeeded event type → skip")
    void nonSucceededEventSkipped() {
        EvaluationContext failedCtx = new EvaluationContext(Tenants.DEFAULT_TENANT_ID,
                "evt-1", "auth.login.failed",
                "acc-1", "1.2.3.***", "fp-1", "US", Instant.now(), null);

        DetectionResult r = new ImpossibleTravelRule(loginHistoryRepository, thresholds)
                .evaluate(failedCtx);

        assertThat(r.fired()).isFalse();
        verifyNoInteractions(loginHistoryRepository);
    }

    @Test
    @DisplayName("Case-insensitive country comparison")
    void caseInsensitiveCountryMatch() {
        Instant now = Instant.parse("2026-04-18T10:00:00Z");
        Instant prev = Instant.parse("2026-04-18T09:30:00Z");

        when(loginHistoryRepository.findLatestSuccessByAccountId(Tenants.DEFAULT_TENANT_ID, "acc-1"))
                .thenReturn(Optional.of(previousLogin("kr", prev)));

        DetectionResult r = new ImpossibleTravelRule(loginHistoryRepository, thresholds)
                .evaluate(succeededCtx("KR", now));

        assertThat(r.fired()).isFalse();
    }

    @Test
    @DisplayName("Configurable score affects outcome")
    void configurableScore() {
        Instant now = Instant.parse("2026-04-18T10:00:00Z");
        Instant prev = Instant.parse("2026-04-18T09:30:00Z");
        DetectionThresholds strict = new DetectionThresholds(
                10, 3600, 80, 900, 85, 15, 50, true, 3600, 85, 60);

        when(loginHistoryRepository.findLatestSuccessByAccountId(Tenants.DEFAULT_TENANT_ID, "acc-1"))
                .thenReturn(Optional.of(previousLogin("KR", prev)));

        DetectionResult r = new ImpossibleTravelRule(loginHistoryRepository, strict)
                .evaluate(succeededCtx("US", now));

        assertThat(r.riskScore()).isEqualTo(85);
        assertThat(RiskLevel.fromScore(r.riskScore())).isEqualTo(RiskLevel.AUTO_LOCK);
    }

    @Test
    @DisplayName("Evidence contains tenantId on fire")
    void evidenceContainsTenantId() {
        Instant now  = Instant.parse("2026-04-18T10:00:00Z");
        Instant prev = Instant.parse("2026-04-18T09:30:00Z");

        when(loginHistoryRepository.findLatestSuccessByAccountId(Tenants.DEFAULT_TENANT_ID, "acc-1"))
                .thenReturn(Optional.of(previousLogin("KR", prev)));

        DetectionResult r = new ImpossibleTravelRule(loginHistoryRepository, thresholds)
                .evaluate(succeededCtx("US", now));

        assertThat(r.fired()).isTrue();
        assertThat(r.evidence()).containsKey("tenantId");
        assertThat(r.evidence().get("tenantId")).isEqualTo(Tenants.DEFAULT_TENANT_ID);
    }

    // ── TASK-BE-248 Phase 2a: Cross-Tenant Isolation ───────────────────────────

    @Test
    @DisplayName("[cross-tenant] 테넌트A 이동 기록이 테넌트B 동일 account 탐지에 영향 없음")
    void crossTenantIsolation_tenantAHistoryDoesNotAffectTenantB() {
        String tenantA = "tenant-a";
        String tenantB = "tenant-b";
        Instant now  = Instant.parse("2026-04-18T10:00:00Z");
        Instant prev = Instant.parse("2026-04-18T09:30:00Z"); // 30분 전, window 내

        ImpossibleTravelRule rule = new ImpossibleTravelRule(loginHistoryRepository, thresholds);

        // tenantA: KR → US within window → fires
        EvaluationContext ctxA = new EvaluationContext(tenantA,
                "evt-a", "auth.login.succeeded", "acc-1",
                "1.2.3.***", "fp-1", "US", now, null);
        when(loginHistoryRepository.findLatestSuccessByAccountId(tenantA, "acc-1"))
                .thenReturn(Optional.of(new LoginHistoryEntry(tenantA,
                        "evt-0", "acc-1", LoginOutcome.SUCCESS,
                        "1.2.3.***", "Chrome", "fp-0", "KR", prev)));
        DetectionResult resultA = rule.evaluate(ctxA);
        assertThat(resultA.fired()).isTrue();

        // tenantB: no previous history → does not fire
        EvaluationContext ctxB = new EvaluationContext(tenantB,
                "evt-b", "auth.login.succeeded", "acc-1",
                "1.2.3.***", "fp-1", "US", now, null);
        when(loginHistoryRepository.findLatestSuccessByAccountId(tenantB, "acc-1"))
                .thenReturn(Optional.empty());
        DetectionResult resultB = rule.evaluate(ctxB);
        assertThat(resultB.fired()).isFalse();

        // Repository must be called with correct tenantId per evaluation
        verify(loginHistoryRepository).findLatestSuccessByAccountId(tenantA, "acc-1");
        verify(loginHistoryRepository).findLatestSuccessByAccountId(tenantB, "acc-1");
    }
}
