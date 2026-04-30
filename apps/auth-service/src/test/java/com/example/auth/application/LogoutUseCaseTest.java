package com.example.auth.application;

import com.example.auth.application.command.LogoutCommand;
import com.example.auth.application.event.AuthEventPublisher;
import com.example.auth.application.exception.TokenParseException;
import com.example.auth.application.port.TokenGeneratorPort;
import com.example.auth.domain.repository.DeviceSessionRepository;
import com.example.auth.domain.repository.RefreshTokenRepository;
import com.example.auth.domain.repository.TokenBlacklist;
import com.example.auth.domain.session.DeviceSession;
import com.example.auth.domain.session.RevokeReason;
import com.example.auth.domain.token.RefreshToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogoutUseCaseTest {

    @Mock
    private TokenGeneratorPort tokenGeneratorPort;
    @Mock
    private TokenBlacklist tokenBlacklist;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private DeviceSessionRepository deviceSessionRepository;
    @Mock
    private AuthEventPublisher authEventPublisher;

    @InjectMocks
    private LogoutUseCase logoutUseCase;

    private static final String ACCOUNT_ID = "acc-123";
    private static final String JTI = "jti-456";
    private static final String DEVICE_ID = "dev-1";

    private RefreshToken activeToken(String deviceId) {
        return new RefreshToken(1L, JTI, ACCOUNT_ID,
                Instant.now().minusSeconds(3600), Instant.now().plusSeconds(600000),
                null, false, "fp-123", deviceId);
    }

    private DeviceSession activeSession() {
        return DeviceSession.create(DEVICE_ID, ACCOUNT_ID, "fp-123", "Chrome/120",
                "127.0.0.1", "KR", Instant.now().minusSeconds(3600));
    }

    @Test
    @DisplayName("Logout revokes refresh token, device_session, and emits auth.session.revoked (USER_REQUESTED)")
    void logoutSuccessRevokesSessionAndPublishesEvent() {
        String refreshTokenStr = "valid-refresh-token";
        when(tokenGeneratorPort.extractJti(refreshTokenStr)).thenReturn(JTI);
        when(tokenGeneratorPort.extractAccountId(refreshTokenStr)).thenReturn(ACCOUNT_ID);
        when(refreshTokenRepository.findByJti(JTI)).thenReturn(Optional.of(activeToken(DEVICE_ID)));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));
        DeviceSession session = activeSession();
        when(deviceSessionRepository.findByDeviceId(DEVICE_ID)).thenReturn(Optional.of(session));
        when(deviceSessionRepository.save(any(DeviceSession.class))).thenAnswer(i -> i.getArgument(0));

        logoutUseCase.execute(new LogoutCommand(refreshTokenStr, DEVICE_ID));

        verify(tokenBlacklist).blacklist(eq(JTI), anyLong());
        verify(refreshTokenRepository).save(any(RefreshToken.class));

        ArgumentCaptor<DeviceSession> savedSession = ArgumentCaptor.forClass(DeviceSession.class);
        verify(deviceSessionRepository).save(savedSession.capture());
        assertThat(savedSession.getValue().isRevoked()).isTrue();
        assertThat(savedSession.getValue().getRevokeReason()).isEqualTo(RevokeReason.USER_REQUESTED);

        verify(authEventPublisher).publishAuthSessionRevoked(
                eq(ACCOUNT_ID),
                eq(DEVICE_ID),
                eq(RevokeReason.USER_REQUESTED.name()),
                eq(List.of(JTI)),
                any(Instant.class),
                eq("USER"),
                eq(ACCOUNT_ID)
        );
    }

    @Test
    @DisplayName("Logout falls back to refresh token device_id when header missing")
    void logoutFallsBackToTokenDeviceId() {
        String refreshTokenStr = "valid-refresh-token";
        when(tokenGeneratorPort.extractJti(refreshTokenStr)).thenReturn(JTI);
        when(tokenGeneratorPort.extractAccountId(refreshTokenStr)).thenReturn(ACCOUNT_ID);
        when(refreshTokenRepository.findByJti(JTI)).thenReturn(Optional.of(activeToken(DEVICE_ID)));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));
        when(deviceSessionRepository.findByDeviceId(DEVICE_ID)).thenReturn(Optional.of(activeSession()));
        when(deviceSessionRepository.save(any(DeviceSession.class))).thenAnswer(i -> i.getArgument(0));

        logoutUseCase.execute(new LogoutCommand(refreshTokenStr)); // no header

        verify(deviceSessionRepository).save(any(DeviceSession.class));
        verify(authEventPublisher).publishAuthSessionRevoked(
                eq(ACCOUNT_ID), eq(DEVICE_ID), eq("USER_REQUESTED"),
                eq(List.of(JTI)), any(Instant.class), eq("USER"), eq(ACCOUNT_ID));
    }

    @Test
    @DisplayName("Logout with legacy token (no device_id) revokes refresh token but skips session event")
    void logoutLegacyTokenSkipsSessionEvent() {
        String refreshTokenStr = "legacy-refresh-token";
        when(tokenGeneratorPort.extractJti(refreshTokenStr)).thenReturn(JTI);
        when(tokenGeneratorPort.extractAccountId(refreshTokenStr)).thenReturn(ACCOUNT_ID);
        when(refreshTokenRepository.findByJti(JTI)).thenReturn(Optional.of(activeToken(null)));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        logoutUseCase.execute(new LogoutCommand(refreshTokenStr));

        verify(tokenBlacklist).blacklist(eq(JTI), anyLong());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
        verify(deviceSessionRepository, never()).save(any(DeviceSession.class));
        verify(authEventPublisher, never()).publishAuthSessionRevoked(
                anyString(), anyString(), anyString(), anyList(), any(), anyString(), any());
    }

    @Test
    @DisplayName("Logout is no-op when refresh token not found in DB")
    void logoutNoEventWhenTokenNotFound() {
        String refreshTokenStr = "unknown-refresh-token";
        when(tokenGeneratorPort.extractJti(refreshTokenStr)).thenReturn("unknown-jti");
        when(tokenGeneratorPort.extractAccountId(refreshTokenStr)).thenReturn(ACCOUNT_ID);
        when(refreshTokenRepository.findByJti("unknown-jti")).thenReturn(Optional.empty());

        logoutUseCase.execute(new LogoutCommand(refreshTokenStr, DEVICE_ID));

        verify(tokenBlacklist, never()).blacklist(anyString(), anyLong());
        verify(authEventPublisher, never()).publishAuthSessionRevoked(
                anyString(), anyString(), anyString(), anyList(), any(), anyString(), any());
    }

    @Test
    @DisplayName("Logout is no-op when token parsing fails")
    void logoutNoEventWhenTokenParsingFails() {
        String refreshTokenStr = "malformed-token";
        when(tokenGeneratorPort.extractJti(refreshTokenStr)).thenThrow(new TokenParseException("Invalid token"));

        logoutUseCase.execute(new LogoutCommand(refreshTokenStr, DEVICE_ID));

        verify(refreshTokenRepository, never()).findByJti(anyString());
        verify(authEventPublisher, never()).publishAuthSessionRevoked(
                anyString(), anyString(), anyString(), anyList(), any(), anyString(), any());
    }
}
