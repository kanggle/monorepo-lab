package com.example.security.application;

import com.example.security.application.event.SecurityEventPublisher;
import com.example.security.domain.detection.*;
import com.example.security.domain.suspicious.SuspiciousEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class DetectSuspiciousActivityUseCaseTest {

    @Mock SuspiciousEventPersistenceService persistenceService;
    @Mock SecurityEventPublisher publisher;
    @Mock IssueAutoLockCommandUseCase issueAutoLockUseCase;
    @Mock SuspiciousActivityRule alertRule;
    @Mock SuspiciousActivityRule autoLockRule;
    @Mock SuspiciousActivityRule quietRule;

    private EvaluationContext ctx() {
        return new EvaluationContext("evt-1", "auth.login.succeeded", "acc-1",
                "1.2.3.***", "fp-1", "US", Instant.now(), null);
    }

    private void stubPersistenceToEchoEvent() {
        when(persistenceService.recordSuspiciousEvent(any(), any(), any()))
                .thenAnswer(inv -> {
                    EvaluationContext c = inv.getArgument(0);
                    RiskScoreAggregator.Aggregated agg = inv.getArgument(1);
                    RiskLevel level = inv.getArgument(2);
                    DetectionResult w = agg.winner();
                    return SuspiciousEvent.create(
                            UUID.randomUUID().toString(),
                            c.accountId(),
                            w.ruleCode(),
                            w.riskScore(),
                            level,
                            w.evidence(),
                            c.eventId(),
                            Instant.now());
                });
    }

    @Test
    @DisplayName("No rule fires → returns null, no persistence, no events")
    void noFire() {
        when(quietRule.evaluate(any())).thenReturn(DetectionResult.NONE);
        DetectSuspiciousActivityUseCase useCase = new DetectSuspiciousActivityUseCase(
                List.of(quietRule), persistenceService, publisher, issueAutoLockUseCase);

        SuspiciousEvent result = useCase.detect(ctx());
        assertThat(result).isNull();
        verifyNoInteractions(persistenceService, publisher, issueAutoLockUseCase);
    }

    @Test
    @DisplayName("ALERT level: persist + publish suspicious.detected, no auto-lock")
    void alertLevel() {
        when(alertRule.evaluate(any()))
                .thenReturn(new DetectionResult("DEVICE_CHANGE", 50, Map.of("k", "v")));
        stubPersistenceToEchoEvent();
        DetectSuspiciousActivityUseCase useCase = new DetectSuspiciousActivityUseCase(
                List.of(alertRule), persistenceService, publisher, issueAutoLockUseCase);

        SuspiciousEvent result = useCase.detect(ctx());
        assertThat(result).isNotNull();
        assertThat(result.getActionTaken()).isEqualTo(RiskLevel.ALERT);
        assertThat(result.getRiskScore()).isEqualTo(50);
        assertThat(result.getRuleCode()).isEqualTo("DEVICE_CHANGE");
        verify(persistenceService).recordSuspiciousEvent(any(), any(), any());
        verify(persistenceService, never()).updateLockResult(any());
        verify(publisher).publishSuspiciousDetected(any());
        verify(publisher, never()).publishAutoLockTriggered(any(), any());
        verify(issueAutoLockUseCase, never()).execute(any());
    }

    @Test
    @DisplayName("AUTO_LOCK: delegates to IssueAutoLockCommandUseCase")
    void autoLockSuccess() {
        when(autoLockRule.evaluate(any()))
                .thenReturn(new DetectionResult("GEO_ANOMALY", 92, Map.of("d", "x")));
        stubPersistenceToEchoEvent();
        doNothing().when(issueAutoLockUseCase).execute(any());

        DetectSuspiciousActivityUseCase useCase = new DetectSuspiciousActivityUseCase(
                List.of(autoLockRule), persistenceService, publisher, issueAutoLockUseCase);

        SuspiciousEvent result = useCase.detect(ctx());
        assertThat(result).isNotNull();
        assertThat(result.getActionTaken()).isEqualTo(RiskLevel.AUTO_LOCK);
        verify(publisher).publishSuspiciousDetected(any());
        verify(issueAutoLockUseCase).execute(any());
    }

    @Test
    @DisplayName("AUTO_LOCK: FAILURE after retries — delegates to IssueAutoLockCommandUseCase")
    void autoLockFailureEmitsPending() {
        when(autoLockRule.evaluate(any()))
                .thenReturn(new DetectionResult("TOKEN_REUSE", 100, Map.of()));
        stubPersistenceToEchoEvent();
        doNothing().when(issueAutoLockUseCase).execute(any());

        DetectSuspiciousActivityUseCase useCase = new DetectSuspiciousActivityUseCase(
                List.of(autoLockRule), persistenceService, publisher, issueAutoLockUseCase);

        useCase.detect(ctx());

        verify(publisher).publishSuspiciousDetected(any());
        verify(issueAutoLockUseCase).execute(any());
    }

    @Test
    @DisplayName("AUTO_LOCK: delegates to IssueAutoLockCommandUseCase regardless of lock outcome")
    void autoLockInvalidTransitionNormalizedToFailure() {
        when(autoLockRule.evaluate(any()))
                .thenReturn(new DetectionResult("GEO_ANOMALY", 95, Map.of()));
        stubPersistenceToEchoEvent();
        doNothing().when(issueAutoLockUseCase).execute(any());

        DetectSuspiciousActivityUseCase useCase = new DetectSuspiciousActivityUseCase(
                List.of(autoLockRule), persistenceService, publisher, issueAutoLockUseCase);

        useCase.detect(ctx());

        verify(issueAutoLockUseCase).execute(any());
    }

    @Test
    @DisplayName("Multiple rules fire — max score wins, winner's ruleCode is persisted")
    void maxScoreWins() {
        when(alertRule.evaluate(any()))
                .thenReturn(new DetectionResult("DEVICE_CHANGE", 50, Map.of()));
        when(autoLockRule.evaluate(any()))
                .thenReturn(new DetectionResult("GEO_ANOMALY", 92, Map.of()));
        stubPersistenceToEchoEvent();
        doNothing().when(issueAutoLockUseCase).execute(any());

        DetectSuspiciousActivityUseCase useCase = new DetectSuspiciousActivityUseCase(
                List.of(alertRule, autoLockRule), persistenceService, publisher, issueAutoLockUseCase);

        SuspiciousEvent result = useCase.detect(ctx());
        assertThat(result.getRuleCode()).isEqualTo("GEO_ANOMALY");
        assertThat(result.getRiskScore()).isEqualTo(92);
    }

    @Test
    @DisplayName("A throwing rule is treated as NONE and does not break the pipeline")
    void throwingRuleIsolated() {
        when(quietRule.evaluate(any())).thenThrow(new RuntimeException("boom"));
        when(alertRule.evaluate(any()))
                .thenReturn(new DetectionResult("DEVICE_CHANGE", 50, Map.of()));
        stubPersistenceToEchoEvent();

        DetectSuspiciousActivityUseCase useCase = new DetectSuspiciousActivityUseCase(
                List.of(quietRule, alertRule), persistenceService, publisher, issueAutoLockUseCase);

        SuspiciousEvent result = useCase.detect(ctx());
        assertThat(result).isNotNull();
        assertThat(result.getRuleCode()).isEqualTo("DEVICE_CHANGE");
    }

    @Test
    @DisplayName("AUTO_LOCK: IssueAutoLockCommandUseCase.execute() is called")
    void lockResultPersisted() {
        when(autoLockRule.evaluate(any()))
                .thenReturn(new DetectionResult("VELOCITY", 96, Map.of()));
        stubPersistenceToEchoEvent();
        doNothing().when(issueAutoLockUseCase).execute(any());

        DetectSuspiciousActivityUseCase useCase = new DetectSuspiciousActivityUseCase(
                List.of(autoLockRule), persistenceService, publisher, issueAutoLockUseCase);

        useCase.detect(ctx());

        verify(issueAutoLockUseCase).execute(any());
    }
}
