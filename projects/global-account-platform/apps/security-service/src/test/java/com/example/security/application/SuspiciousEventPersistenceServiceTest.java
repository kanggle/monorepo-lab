package com.example.security.application;

import com.example.security.domain.Tenants;
import com.example.security.domain.detection.DetectionResult;
import com.example.security.domain.detection.EvaluationContext;
import com.example.security.domain.detection.RiskLevel;
import com.example.security.domain.detection.RiskScoreAggregator;
import com.example.security.domain.repository.SuspiciousEventRepository;
import com.example.security.domain.suspicious.SuspiciousEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SuspiciousEventPersistenceService 단위 테스트")
class SuspiciousEventPersistenceServiceTest {

    @Mock
    private SuspiciousEventRepository suspiciousEventRepository;

    @InjectMocks
    private SuspiciousEventPersistenceService service;

    // ── recordSuspiciousEvent ─────────────────────────────────────────────────

    @Test
    @DisplayName("recordSuspiciousEvent — 이벤트 저장 및 반환값 필드 검증")
    void recordSuspiciousEvent_validInput_savesAndReturnsEvent() {
        EvaluationContext ctx = evaluationContext("acc-1", "evt-1");
        DetectionResult winner = new DetectionResult("VELOCITY", 80, Map.of("count", 10));
        RiskScoreAggregator.Aggregated aggregated =
                new RiskScoreAggregator.Aggregated(winner, List.of(winner));
        SuspiciousEvent result = service.recordSuspiciousEvent(ctx, aggregated, RiskLevel.AUTO_LOCK);

        assertThat(result.getTenantId()).isEqualTo(Tenants.DEFAULT_TENANT_ID);
        assertThat(result.getAccountId()).isEqualTo("acc-1");
        assertThat(result.getRuleCode()).isEqualTo("VELOCITY");
        assertThat(result.getRiskScore()).isEqualTo(80);
        assertThat(result.getActionTaken()).isEqualTo(RiskLevel.AUTO_LOCK);
        assertThat(result.getTriggerEventId()).isEqualTo("evt-1");
        verify(suspiciousEventRepository).save(any(SuspiciousEvent.class));
    }

    @Test
    @DisplayName("recordSuspiciousEvent — evidence null 이어도 저장 성공")
    void recordSuspiciousEvent_nullEvidence_savesSuccessfully() {
        EvaluationContext ctx = evaluationContext("acc-2", "evt-2");
        DetectionResult winner = new DetectionResult("GEO_ANOMALY", 60, null);
        RiskScoreAggregator.Aggregated aggregated =
                new RiskScoreAggregator.Aggregated(winner, List.of(winner));
        SuspiciousEvent result = service.recordSuspiciousEvent(ctx, aggregated, RiskLevel.ALERT);

        assertThat(result.getEvidence()).isEmpty();
        assertThat(result.getActionTaken()).isEqualTo(RiskLevel.ALERT);
        verify(suspiciousEventRepository).save(any(SuspiciousEvent.class));
    }

    // ── updateLockResult ──────────────────────────────────────────────────────

    @Test
    @DisplayName("updateLockResult — save 1회 호출 검증")
    void updateLockResult_callsSaveOnce() {
        SuspiciousEvent event = SuspiciousEvent.create(
                "evt-id", Tenants.DEFAULT_TENANT_ID, "acc-3", "VELOCITY", 80, RiskLevel.AUTO_LOCK,
                Map.of(), "trigger-1", Instant.now());
        SuspiciousEvent updated = event.withLockRequestResult("SUCCESS");

        service.updateLockResult(updated);

        verify(suspiciousEventRepository).save(updated);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static EvaluationContext evaluationContext(String accountId, String eventId) {
        return new EvaluationContext(
                Tenants.DEFAULT_TENANT_ID,
                eventId, "auth.login.failed", accountId,
                "1.2.3.x", "fp-abc", "KR", Instant.now(), 5);
    }
}
