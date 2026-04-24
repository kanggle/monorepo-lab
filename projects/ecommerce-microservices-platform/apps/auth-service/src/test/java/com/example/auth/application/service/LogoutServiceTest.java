package com.example.auth.application.service;

import com.example.auth.application.dto.LogoutCommand;
import com.example.auth.application.exception.InvalidRefreshTokenException;
import com.example.auth.domain.event.AuthEventPublisher;
import com.example.auth.domain.repository.AccessTokenBlocklist;
import com.example.auth.domain.service.AuthMetricsRecorder;
import com.example.auth.domain.repository.RefreshTokenStore;
import com.example.auth.domain.repository.UserSessionRegistry;
import com.example.auth.domain.service.TokenProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LogoutService 단위 테스트")
class LogoutServiceTest {

    @InjectMocks
    private LogoutService logoutService;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    @Mock
    private TokenProperties tokenProperties;

    @Mock
    private AccessTokenBlocklist accessTokenBlocklist;

    @Mock
    private UserSessionRegistry sessionRegistry;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private AuthEventPublisher eventPublisher;

    @Mock
    private AuthMetricsRecorder authMetrics;

    @Test
    @DisplayName("로그아웃 시 refresh token invalidate + access token blacklist 호출")
    void logout_invalidatesToken() {
        String refreshToken = "some-refresh-token";
        String accessToken = "some-access-token";
        UUID userId = UUID.randomUUID();

        given(refreshTokenStore.findUserIdByToken(refreshToken)).willReturn(Optional.of(userId));
        given(tokenProperties.refreshTokenTtlSeconds()).willReturn(2592000L);
        given(tokenProperties.accessTokenTtlSeconds()).willReturn(3600L);

        logoutService.logout(new LogoutCommand(refreshToken, accessToken, userId, "user@example.com", "127.0.0.1", "Mozilla/5.0"));

        then(refreshTokenStore).should().invalidate(eq(refreshToken), eq(2592000L));
        then(sessionRegistry).should().removeSession(eq(userId), eq(refreshToken));
        then(accessTokenBlocklist).should().block(eq(accessToken), eq(3600L));
        then(auditLogService).should().recordLogout(eq(userId), eq("user@example.com"), eq("127.0.0.1"), eq("Mozilla/5.0"));
        then(eventPublisher).should().publish(argThat(e -> e.eventType().equals("UserLoggedOut")));
    }

    @Test
    @DisplayName("토큰이 존재하지 않으면 InvalidRefreshTokenException")
    void logout_tokenNotFound_throws() {
        String token = "unknown-token";
        UUID userId = UUID.randomUUID();
        given(refreshTokenStore.findUserIdByToken(token)).willReturn(Optional.empty());

        assertThatThrownBy(() -> logoutService.logout(new LogoutCommand(token, "some-access-token", userId, null, null, null)))
            .isInstanceOf(InvalidRefreshTokenException.class)
            .hasMessage("Refresh token not found or expired");

        then(auditLogService).should(never()).recordLogout(any(), any(), any(), any());
        then(eventPublisher).should(never()).publish(any());
    }

    @Test
    @DisplayName("토큰 소유자와 요청 사용자가 다르면 InvalidRefreshTokenException")
    void logout_differentUser_throws() {
        String token = "some-refresh-token";
        UUID tokenOwner = UUID.randomUUID();
        UUID requestUser = UUID.randomUUID();
        given(refreshTokenStore.findUserIdByToken(token)).willReturn(Optional.of(tokenOwner));

        assertThatThrownBy(() -> logoutService.logout(new LogoutCommand(token, "some-access-token", requestUser, null, null, null)))
            .isInstanceOf(InvalidRefreshTokenException.class)
            .hasMessage("Refresh token not found or expired");

        then(auditLogService).should(never()).recordLogout(any(), any(), any(), any());
        then(eventPublisher).should(never()).publish(any());
    }

    @Test
    @DisplayName("RefreshTokenStore.invalidate()가 예외를 던지면 그대로 전파된다")
    void logout_storeThrows_propagates() {
        String token = "some-refresh-token";
        UUID userId = UUID.randomUUID();
        given(refreshTokenStore.findUserIdByToken(token)).willReturn(Optional.of(userId));
        given(tokenProperties.refreshTokenTtlSeconds()).willReturn(2592000L);
        willThrow(new org.springframework.dao.QueryTimeoutException("Redis connection failed"))
            .given(refreshTokenStore).invalidate(anyString(), anyLong());

        assertThatThrownBy(() -> logoutService.logout(new LogoutCommand(token, "some-access-token", userId, null, null, null)))
            .isInstanceOf(org.springframework.dao.DataAccessException.class)
            .hasMessageContaining("Redis connection failed");

        then(sessionRegistry).should(never()).removeSession(any(), any());
        then(auditLogService).should(never()).recordLogout(any(), any(), any(), any());
        then(eventPublisher).should(never()).publish(any());
    }

    @Test
    @DisplayName("accessTokenBlocklist.block()이 실패해도 로그아웃은 성공한다")
    void logout_accessTokenBlocklistFails_doesNotThrow() {
        String refreshToken = "some-refresh-token";
        String accessToken = "some-access-token";
        UUID userId = UUID.randomUUID();

        given(refreshTokenStore.findUserIdByToken(refreshToken)).willReturn(Optional.of(userId));
        given(tokenProperties.refreshTokenTtlSeconds()).willReturn(2592000L);
        given(tokenProperties.accessTokenTtlSeconds()).willReturn(3600L);
        willThrow(new org.springframework.dao.QueryTimeoutException("Redis timeout"))
            .given(accessTokenBlocklist).block(anyString(), anyLong());

        assertThatCode(
            () -> logoutService.logout(new LogoutCommand(refreshToken, accessToken, userId, "user@example.com", null, null))
        ).doesNotThrowAnyException();

        then(refreshTokenStore).should().invalidate(eq(refreshToken), eq(2592000L));
        then(sessionRegistry).should().removeSession(eq(userId), eq(refreshToken));
        then(auditLogService).should().recordLogout(eq(userId), eq("user@example.com"), isNull(), isNull());
        then(eventPublisher).should().publish(argThat(e -> e.eventType().equals("UserLoggedOut")));
    }
}
