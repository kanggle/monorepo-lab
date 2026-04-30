package com.example.security.application;

import com.example.security.application.event.SecurityEventPublisher;
import com.example.security.domain.detection.AccountLockClient;
import com.example.security.domain.detection.RiskLevel;
import com.example.security.domain.suspicious.SuspiciousEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class IssueAutoLockCommandUseCaseTest {

    @Mock AccountLockClient accountLockClient;
    @Mock SuspiciousEventPersistenceService persistenceService;
    @Mock SecurityEventPublisher publisher;
    @InjectMocks IssueAutoLockCommandUseCase useCase;

    private SuspiciousEvent event() {
        return SuspiciousEvent.create(UUID.randomUUID().toString(), "acc-1",
                "GEO_ANOMALY", 92, RiskLevel.AUTO_LOCK, null, "evt-1", Instant.now());
    }

    @Test
    @DisplayName("SUCCESS — updateLockResult('SUCCESS'), publishAutoLockTriggered(SUCCESS), no pending")
    void execute_success_updatesResultAndPublishesTriggered() {
        when(accountLockClient.lock(any()))
                .thenReturn(new AccountLockClient.LockResult(AccountLockClient.Status.SUCCESS, 200, "{}"));

        useCase.execute(event());

        verify(persistenceService).updateLockResult(argThat(e -> "SUCCESS".equals(e.getLockRequestResult())));
        verify(publisher).publishAutoLockTriggered(any(), eq(AccountLockClient.Status.SUCCESS));
        verify(publisher, never()).publishAutoLockPending(any());
    }

    @Test
    @DisplayName("FAILURE — updateLockResult('FAILURE'), publishAutoLockTriggered(FAILURE), publishAutoLockPending")
    void execute_failure_updatesResultAndPublishesPending() {
        when(accountLockClient.lock(any()))
                .thenReturn(new AccountLockClient.LockResult(AccountLockClient.Status.FAILURE, 0, "timeout"));

        useCase.execute(event());

        verify(persistenceService).updateLockResult(argThat(e -> "FAILURE".equals(e.getLockRequestResult())));
        verify(publisher).publishAutoLockTriggered(any(), eq(AccountLockClient.Status.FAILURE));
        verify(publisher).publishAutoLockPending(any());
    }

    @Test
    @DisplayName("ALREADY_LOCKED — updateLockResult('ALREADY_LOCKED'), no pending")
    void execute_alreadyLocked_updatesResult() {
        when(accountLockClient.lock(any()))
                .thenReturn(new AccountLockClient.LockResult(AccountLockClient.Status.ALREADY_LOCKED, 200, "{}"));

        useCase.execute(event());

        verify(persistenceService).updateLockResult(argThat(e -> "ALREADY_LOCKED".equals(e.getLockRequestResult())));
        verify(publisher).publishAutoLockTriggered(any(), eq(AccountLockClient.Status.ALREADY_LOCKED));
        verify(publisher, never()).publishAutoLockPending(any());
    }

    @Test
    @DisplayName("INVALID_TRANSITION — normalized to 'FAILURE' in persisted row")
    void execute_invalidTransition_normalizedToFailure() {
        when(accountLockClient.lock(any()))
                .thenReturn(new AccountLockClient.LockResult(AccountLockClient.Status.INVALID_TRANSITION, 409, "{}"));

        useCase.execute(event());

        verify(persistenceService).updateLockResult(argThat(e -> "FAILURE".equals(e.getLockRequestResult())));
    }
}
