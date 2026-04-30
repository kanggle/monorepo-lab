package com.example.auth.application;

import com.example.auth.application.event.AuthEventPublisher;
import com.example.auth.application.exception.SessionNotFoundException;
import com.example.auth.application.exception.SessionOwnershipMismatchException;
import com.example.auth.domain.repository.DeviceSessionRepository;
import com.example.auth.domain.repository.RefreshTokenRepository;
import com.example.auth.domain.session.DeviceSession;
import com.example.auth.domain.session.RevokeReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RevokeSessionUseCase 단위 테스트")
class RevokeSessionUseCaseTest {

    @Mock
    private DeviceSessionRepository deviceSessionRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private AuthEventPublisher authEventPublisher;

    @InjectMocks
    private RevokeSessionUseCase useCase;

    private static final String ACCOUNT_ID = "acc-revoke";
    private static final String DEVICE_ID = "dev-revoke";

    @Test
    @DisplayName("정상 revoke — 토큰 취소 및 이벤트 발행")
    void execute_activeSession_revokesAndPublishesEvent() {
        DeviceSession session = activeSession(DEVICE_ID, ACCOUNT_ID);
        when(deviceSessionRepository.findByDeviceId(DEVICE_ID)).thenReturn(Optional.of(session));
        when(refreshTokenRepository.findActiveJtisByDeviceId(DEVICE_ID)).thenReturn(List.of("jti-1"));

        useCase.execute(ACCOUNT_ID, DEVICE_ID);

        assertThat(session.isRevoked()).isTrue();
        assertThat(session.getRevokeReason()).isEqualTo(RevokeReason.USER_REQUESTED);
        verify(refreshTokenRepository).revokeAllByDeviceId(DEVICE_ID);
        verify(deviceSessionRepository).save(session);
        verify(authEventPublisher).publishAuthSessionRevoked(
                eq(ACCOUNT_ID), eq(DEVICE_ID),
                eq(RevokeReason.USER_REQUESTED.name()),
                anyList(), any(Instant.class),
                anyString(), anyString());
    }

    @Test
    @DisplayName("세션 미존재 — SessionNotFoundException")
    void execute_sessionNotFound_throwsSessionNotFound() {
        when(deviceSessionRepository.findByDeviceId(DEVICE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(ACCOUNT_ID, DEVICE_ID))
                .isInstanceOf(SessionNotFoundException.class);
        verify(refreshTokenRepository, never()).revokeAllByDeviceId(anyString());
    }

    @Test
    @DisplayName("타 계정 세션 — SessionOwnershipMismatchException")
    void execute_crossAccountSession_throwsOwnershipMismatch() {
        DeviceSession session = activeSession(DEVICE_ID, "other-account");
        when(deviceSessionRepository.findByDeviceId(DEVICE_ID)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> useCase.execute(ACCOUNT_ID, DEVICE_ID))
                .isInstanceOf(SessionOwnershipMismatchException.class);
        verify(refreshTokenRepository, never()).revokeAllByDeviceId(anyString());
    }

    @Test
    @DisplayName("이미 revoked 세션 — idempotent하게 SessionNotFoundException")
    void execute_alreadyRevokedSession_throwsSessionNotFound() {
        DeviceSession session = revokedSession(DEVICE_ID, ACCOUNT_ID);
        when(deviceSessionRepository.findByDeviceId(DEVICE_ID)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> useCase.execute(ACCOUNT_ID, DEVICE_ID))
                .isInstanceOf(SessionNotFoundException.class);
        verify(refreshTokenRepository, never()).revokeAllByDeviceId(anyString());
    }

    private static DeviceSession activeSession(String deviceId, String accountId) {
        Instant now = Instant.now();
        return new DeviceSession(1L, deviceId, accountId, "fp", "UA", "1.1.1.1", "KR",
                now.minusSeconds(3600), now.minusSeconds(60), null, null);
    }

    private static DeviceSession revokedSession(String deviceId, String accountId) {
        Instant now = Instant.now();
        return new DeviceSession(2L, deviceId, accountId, "fp", "UA", "1.1.1.1", "KR",
                now.minusSeconds(3600), now.minusSeconds(60),
                now.minusSeconds(30), RevokeReason.USER_REQUESTED);
    }
}
