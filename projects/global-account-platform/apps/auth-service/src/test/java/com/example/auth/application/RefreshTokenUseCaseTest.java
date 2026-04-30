package com.example.auth.application;

import com.example.auth.application.command.RefreshTokenCommand;
import com.example.auth.application.event.AuthEventPublisher;
import com.example.auth.application.exception.SessionRevokedException;
import com.example.auth.application.exception.TokenExpiredException;
import com.example.auth.application.exception.TokenParseException;
import com.example.auth.application.exception.TokenReuseDetectedException;
import com.example.auth.application.exception.TokenTenantMismatchException;
import com.example.auth.application.port.TokenGeneratorPort;
import com.example.auth.application.result.RefreshTokenResult;
import com.example.auth.domain.repository.BulkInvalidationStore;
import com.example.auth.domain.repository.DeviceSessionRepository;
import com.example.auth.domain.repository.RefreshTokenRepository;
import com.example.auth.domain.repository.TokenBlacklist;
import com.example.auth.domain.session.DeviceSession;
import com.example.auth.domain.session.RevokeReason;
import com.example.auth.domain.session.SessionContext;
import com.example.auth.domain.tenant.TenantContext;
import com.example.auth.domain.token.RefreshToken;
import com.example.auth.domain.token.TokenPair;
import com.example.auth.domain.token.TokenReuseDetector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenUseCaseTest {

    @Mock
    private TokenGeneratorPort tokenGeneratorPort;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private TokenBlacklist tokenBlacklist;
    @Mock
    private TokenReuseDetector tokenReuseDetector;
    @Mock
    private BulkInvalidationStore bulkInvalidationStore;
    @Mock
    private AuthEventPublisher authEventPublisher;
    @Mock
    private DeviceSessionRepository deviceSessionRepository;

    @InjectMocks
    private RefreshTokenUseCase refreshTokenUseCase;

    private static final String ACCOUNT_ID = "acc-123";
    private static final String TENANT_ID = "fan-platform";
    private static final String OLD_JTI = "old-jti";
    private static final String NEW_JTI = "new-jti";
    private static final SessionContext CTX = new SessionContext("127.0.0.1", "Chrome/120", "fp-123");

    private RefreshToken activeToken(String jti, String tenantId) {
        return new RefreshToken(1L, jti, ACCOUNT_ID, tenantId,
                Instant.now().minusSeconds(3600), Instant.now().plusSeconds(600000),
                null, false, "fp-123", null);
    }

    @Test
    @DisplayName("Refresh token rotation succeeds (tenant_id matches)")
    void refreshSuccess() {
        // given
        String refreshTokenStr = "old-refresh-token";
        when(tokenGeneratorPort.extractJti(refreshTokenStr)).thenReturn(OLD_JTI);
        when(tokenGeneratorPort.extractAccountId(refreshTokenStr)).thenReturn(ACCOUNT_ID);
        when(tokenGeneratorPort.extractTenantId(refreshTokenStr)).thenReturn(TENANT_ID);
        when(tokenBlacklist.isBlacklisted(OLD_JTI)).thenReturn(false);
        when(bulkInvalidationStore.getInvalidatedAt(ACCOUNT_ID)).thenReturn(Optional.empty());

        RefreshToken existingToken = activeToken(OLD_JTI, TENANT_ID);
        when(refreshTokenRepository.findByJti(OLD_JTI)).thenReturn(Optional.of(existingToken));
        when(tokenReuseDetector.isReuse(existingToken)).thenReturn(false);
        when(tokenGeneratorPort.generateTokenPair(eq(ACCOUNT_ID), eq("user"),
                nullable(String.class), any(TenantContext.class)))
                .thenReturn(new TokenPair("new-access", "new-refresh", 1800));
        when(tokenGeneratorPort.extractJti("new-refresh")).thenReturn(NEW_JTI);
        when(tokenGeneratorPort.refreshTokenTtlSeconds()).thenReturn(604800L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        // when
        RefreshTokenResult result = refreshTokenUseCase.execute(
                new RefreshTokenCommand(refreshTokenStr, CTX));

        // then
        assertThat(result.accessToken()).isEqualTo("new-access");
        assertThat(result.refreshToken()).isEqualTo("new-refresh");
        verify(tokenBlacklist).blacklist(eq(OLD_JTI), anyLong());
        verify(authEventPublisher).publishTokenRefreshed(ACCOUNT_ID, TENANT_ID, OLD_JTI, NEW_JTI, CTX);
        verify(bulkInvalidationStore, never()).invalidateAll(anyString(), anyLong());
    }

    @Test
    @DisplayName("Refresh fails with TOKEN_TENANT_MISMATCH when JWT tenant_id differs from DB row")
    void refreshFailsTokenTenantMismatch() {
        // given
        String refreshTokenStr = "cross-tenant-token";
        when(tokenGeneratorPort.extractJti(refreshTokenStr)).thenReturn(OLD_JTI);
        when(tokenGeneratorPort.extractAccountId(refreshTokenStr)).thenReturn(ACCOUNT_ID);
        // Token claims "wms" but DB row has "fan-platform"
        when(tokenGeneratorPort.extractTenantId(refreshTokenStr)).thenReturn("wms");

        RefreshToken existingToken = activeToken(OLD_JTI, TENANT_ID); // DB has fan-platform
        when(refreshTokenRepository.findByJti(OLD_JTI)).thenReturn(Optional.of(existingToken));
        when(tokenReuseDetector.isReuse(existingToken)).thenReturn(false);

        // when/then
        assertThatThrownBy(() -> refreshTokenUseCase.execute(
                new RefreshTokenCommand(refreshTokenStr, CTX)))
                .isInstanceOf(TokenTenantMismatchException.class);

        // Security event must be emitted
        verify(authEventPublisher).publishTokenTenantMismatch(
                eq(ACCOUNT_ID), eq("wms"), eq(TENANT_ID), eq(OLD_JTI),
                eq(CTX.ipMasked()), eq(CTX.deviceFingerprint()));
    }

    @Test
    @DisplayName("Refresh fails when token is blacklisted (no reuse chain)")
    void refreshFailsBlacklisted() {
        String refreshTokenStr = "blacklisted-token";
        when(tokenGeneratorPort.extractJti(refreshTokenStr)).thenReturn(OLD_JTI);
        when(tokenGeneratorPort.extractAccountId(refreshTokenStr)).thenReturn(ACCOUNT_ID);
        when(tokenGeneratorPort.extractTenantId(refreshTokenStr)).thenReturn(TENANT_ID);

        RefreshToken existingToken = activeToken(OLD_JTI, TENANT_ID);
        when(refreshTokenRepository.findByJti(OLD_JTI)).thenReturn(Optional.of(existingToken));
        when(tokenReuseDetector.isReuse(existingToken)).thenReturn(false);
        when(tokenBlacklist.isBlacklisted(OLD_JTI)).thenReturn(true);

        assertThatThrownBy(() -> refreshTokenUseCase.execute(
                new RefreshTokenCommand(refreshTokenStr, CTX)))
                .isInstanceOf(SessionRevokedException.class);
    }

    @Test
    @DisplayName("Refresh fails when token not found in DB")
    void refreshFailsNotFound() {
        String refreshTokenStr = "unknown-token";
        when(tokenGeneratorPort.extractJti(refreshTokenStr)).thenReturn("unknown-jti");
        when(tokenGeneratorPort.extractAccountId(refreshTokenStr)).thenReturn(ACCOUNT_ID);
        when(tokenGeneratorPort.extractTenantId(refreshTokenStr)).thenReturn(TENANT_ID);
        when(refreshTokenRepository.findByJti("unknown-jti")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenUseCase.execute(
                new RefreshTokenCommand(refreshTokenStr, CTX)))
                .isInstanceOf(TokenExpiredException.class);
    }

    @Test
    @DisplayName("Refresh fails when token has been revoked but no reuse chain")
    void refreshFailsRevoked() {
        String refreshTokenStr = "revoked-token";
        when(tokenGeneratorPort.extractJti(refreshTokenStr)).thenReturn(OLD_JTI);
        when(tokenGeneratorPort.extractAccountId(refreshTokenStr)).thenReturn(ACCOUNT_ID);
        when(tokenGeneratorPort.extractTenantId(refreshTokenStr)).thenReturn(TENANT_ID);
        when(tokenBlacklist.isBlacklisted(OLD_JTI)).thenReturn(false);
        when(bulkInvalidationStore.getInvalidatedAt(ACCOUNT_ID)).thenReturn(Optional.empty());

        RefreshToken revokedToken = new RefreshToken(1L, OLD_JTI, ACCOUNT_ID, TENANT_ID,
                Instant.now().minusSeconds(3600), Instant.now().plusSeconds(600000),
                null, true, "fp-123", null);
        when(refreshTokenRepository.findByJti(OLD_JTI)).thenReturn(Optional.of(revokedToken));
        when(tokenReuseDetector.isReuse(revokedToken)).thenReturn(false);

        assertThatThrownBy(() -> refreshTokenUseCase.execute(
                new RefreshTokenCommand(refreshTokenStr, CTX)))
                .isInstanceOf(SessionRevokedException.class);
    }

    @Test
    @DisplayName("Reuse detected: revokes all device_sessions, emits per-device auth.session.revoked with TOKEN_REUSE")
    void refreshFailsReuseDetected() {
        String refreshTokenStr = "reused-token";
        when(tokenGeneratorPort.extractJti(refreshTokenStr)).thenReturn(OLD_JTI);
        when(tokenGeneratorPort.extractAccountId(refreshTokenStr)).thenReturn(ACCOUNT_ID);
        when(tokenGeneratorPort.extractTenantId(refreshTokenStr)).thenReturn(TENANT_ID);

        RefreshToken existingToken = activeToken(OLD_JTI, TENANT_ID);
        when(refreshTokenRepository.findByJti(OLD_JTI)).thenReturn(Optional.of(existingToken));
        when(tokenReuseDetector.isReuse(existingToken)).thenReturn(true);
        when(refreshTokenRepository.revokeAllByAccountId(ACCOUNT_ID)).thenReturn(3);
        when(tokenGeneratorPort.refreshTokenTtlSeconds()).thenReturn(604800L);

        DeviceSession s1 = DeviceSession.create("dev-1", ACCOUNT_ID, "fp-1", "Chrome",
                "127.0.0.1", "KR", Instant.now().minusSeconds(3600));
        DeviceSession s2 = DeviceSession.create("dev-2", ACCOUNT_ID, "fp-2", "Safari",
                "127.0.0.2", "KR", Instant.now().minusSeconds(1800));
        when(deviceSessionRepository.findActiveByAccountId(ACCOUNT_ID))
                .thenReturn(List.of(s1, s2));
        when(refreshTokenRepository.findActiveJtisByDeviceId("dev-1"))
                .thenReturn(List.of(OLD_JTI, "sibling-jti-1"));
        when(refreshTokenRepository.findActiveJtisByDeviceId("dev-2"))
                .thenReturn(List.of("sibling-jti-2"));
        when(deviceSessionRepository.save(any(DeviceSession.class)))
                .thenAnswer(i -> i.getArgument(0));

        assertThatThrownBy(() -> refreshTokenUseCase.execute(
                new RefreshTokenCommand(refreshTokenStr, CTX)))
                .isInstanceOf(TokenReuseDetectedException.class);

        verify(refreshTokenRepository).revokeAllByAccountId(ACCOUNT_ID);
        verify(bulkInvalidationStore).invalidateAll(ACCOUNT_ID, 604800L);
        verify(authEventPublisher).publishTokenReuseDetected(
                eq(ACCOUNT_ID), eq(OLD_JTI), any(), any(Instant.class),
                eq(CTX.ipMasked()), eq(CTX.deviceFingerprint()),
                eq(true), eq(3)
        );

        ArgumentCaptor<DeviceSession> savedSessions = ArgumentCaptor.forClass(DeviceSession.class);
        verify(deviceSessionRepository, times(2)).save(savedSessions.capture());
        assertThat(savedSessions.getAllValues())
                .allMatch(DeviceSession::isRevoked)
                .allMatch(s -> s.getRevokeReason() == RevokeReason.TOKEN_REUSE);

        verify(authEventPublisher).publishAuthSessionRevoked(
                eq(ACCOUNT_ID), eq("dev-1"), eq(RevokeReason.TOKEN_REUSE.name()),
                eq(List.of(OLD_JTI, "sibling-jti-1")), any(Instant.class),
                eq("SYSTEM"), isNull());
        verify(authEventPublisher).publishAuthSessionRevoked(
                eq(ACCOUNT_ID), eq("dev-2"), eq(RevokeReason.TOKEN_REUSE.name()),
                eq(List.of("sibling-jti-2")), any(Instant.class),
                eq("SYSTEM"), isNull());
    }

    @Test
    @DisplayName("Refresh fails with SESSION_REVOKED when invalidate-all marker exists and token iat precedes it")
    void refreshFailsWhenInvalidateAllMarkerPredatesToken() {
        String refreshTokenStr = "stale-token";
        Instant markerAt = Instant.now().minusSeconds(60);
        Instant tokenIat = markerAt.minusSeconds(60);
        when(tokenGeneratorPort.extractJti(refreshTokenStr)).thenReturn(OLD_JTI);
        when(tokenGeneratorPort.extractAccountId(refreshTokenStr)).thenReturn(ACCOUNT_ID);
        when(tokenGeneratorPort.extractTenantId(refreshTokenStr)).thenReturn(TENANT_ID);

        RefreshToken existingToken = new RefreshToken(1L, OLD_JTI, ACCOUNT_ID, TENANT_ID,
                tokenIat, tokenIat.plusSeconds(600000), null, false, "fp-123", null);
        when(refreshTokenRepository.findByJti(OLD_JTI)).thenReturn(Optional.of(existingToken));
        when(tokenReuseDetector.isReuse(existingToken)).thenReturn(false);
        when(tokenBlacklist.isBlacklisted(OLD_JTI)).thenReturn(false);
        when(bulkInvalidationStore.getInvalidatedAt(ACCOUNT_ID)).thenReturn(Optional.of(markerAt));
        when(tokenGeneratorPort.extractIssuedAt(refreshTokenStr)).thenReturn(tokenIat);

        assertThatThrownBy(() -> refreshTokenUseCase.execute(
                new RefreshTokenCommand(refreshTokenStr, CTX)))
                .isInstanceOf(SessionRevokedException.class);
    }

    @Test
    @DisplayName("JWT 파싱 실패 시 TokenExpiredException 으로 매핑된다 (fail-closed)")
    void refreshFailsWhenTokenParseFails() {
        String refreshTokenStr = "malformed-token";
        when(tokenGeneratorPort.extractJti(refreshTokenStr))
                .thenThrow(new TokenParseException("JWT parse error"));

        assertThatThrownBy(() -> refreshTokenUseCase.execute(
                new RefreshTokenCommand(refreshTokenStr, CTX)))
                .isInstanceOf(TokenExpiredException.class);
    }

    @Test
    @DisplayName("invalidate-all 마커 존재 시 iat 추출 실패하면 SessionRevokedException (fail-closed)")
    void refreshFailsWhenIatExtractFails() {
        String refreshTokenStr = "valid-but-iat-fails";
        Instant markerAt = Instant.now().minusSeconds(60);
        when(tokenGeneratorPort.extractJti(refreshTokenStr)).thenReturn(OLD_JTI);
        when(tokenGeneratorPort.extractAccountId(refreshTokenStr)).thenReturn(ACCOUNT_ID);
        when(tokenGeneratorPort.extractTenantId(refreshTokenStr)).thenReturn(TENANT_ID);

        RefreshToken existingToken = activeToken(OLD_JTI, TENANT_ID);
        when(refreshTokenRepository.findByJti(OLD_JTI)).thenReturn(Optional.of(existingToken));
        when(tokenReuseDetector.isReuse(existingToken)).thenReturn(false);
        when(tokenBlacklist.isBlacklisted(OLD_JTI)).thenReturn(false);
        when(bulkInvalidationStore.getInvalidatedAt(ACCOUNT_ID)).thenReturn(Optional.of(markerAt));
        when(tokenGeneratorPort.extractIssuedAt(refreshTokenStr))
                .thenThrow(new TokenParseException("iat parse error"));

        assertThatThrownBy(() -> refreshTokenUseCase.execute(
                new RefreshTokenCommand(refreshTokenStr, CTX)))
                .isInstanceOf(SessionRevokedException.class);
    }

    @Test
    @DisplayName("Reuse on already-revoked chain is idempotent: throws 401 but does not re-emit events")
    void refreshReuseIdempotentOnAlreadyRevoked() {
        String refreshTokenStr = "reused-revoked-token";
        when(tokenGeneratorPort.extractJti(refreshTokenStr)).thenReturn(OLD_JTI);
        when(tokenGeneratorPort.extractAccountId(refreshTokenStr)).thenReturn(ACCOUNT_ID);
        when(tokenGeneratorPort.extractTenantId(refreshTokenStr)).thenReturn(TENANT_ID);

        RefreshToken existingToken = new RefreshToken(1L, OLD_JTI, ACCOUNT_ID, TENANT_ID,
                Instant.now().minusSeconds(3600), Instant.now().plusSeconds(600000),
                null, true, "fp-123", null); // already revoked
        when(refreshTokenRepository.findByJti(OLD_JTI)).thenReturn(Optional.of(existingToken));
        when(tokenReuseDetector.isReuse(existingToken)).thenReturn(true);
        when(deviceSessionRepository.findActiveByAccountId(ACCOUNT_ID)).thenReturn(List.of());
        when(refreshTokenRepository.revokeAllByAccountId(ACCOUNT_ID)).thenReturn(0);
        when(tokenGeneratorPort.refreshTokenTtlSeconds()).thenReturn(604800L);

        assertThatThrownBy(() -> refreshTokenUseCase.execute(
                new RefreshTokenCommand(refreshTokenStr, CTX)))
                .isInstanceOf(TokenReuseDetectedException.class);

        verify(bulkInvalidationStore).invalidateAll(ACCOUNT_ID, 604800L);
        verify(authEventPublisher, never()).publishTokenReuseDetected(
                anyString(), anyString(), any(), any(), any(), any(), anyBoolean(), anyInt());
        verify(authEventPublisher, never()).publishAuthSessionRevoked(
                anyString(), anyString(), anyString(), anyList(), any(Instant.class), anyString(), any());
        verify(deviceSessionRepository, never()).save(any());
    }
}
