package com.example.auth.application.service;

import com.example.auth.application.dto.RefreshCommand;
import com.example.auth.application.dto.RefreshResult;
import com.example.auth.application.exception.InvalidRefreshTokenException;
import com.example.auth.application.exception.RefreshTokenRevokedException;
import com.example.auth.domain.entity.User;
import com.example.auth.domain.event.AuthEventPublisher;
import com.example.auth.domain.repository.RefreshTokenStore;
import com.example.auth.domain.service.AuthMetricsRecorder;
import com.example.auth.domain.repository.UserRepository;
import com.example.auth.domain.repository.UserSessionRegistry;
import com.example.auth.domain.service.SessionProperties;
import com.example.auth.domain.service.TokenGenerator;
import com.example.auth.domain.service.TokenProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshTokenService 단위 테스트")
class RefreshTokenServiceTest {

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenGenerator tokenGenerator;

    @Mock
    private TokenProperties tokenProperties;

    @Mock
    private SessionProperties sessionProperties;

    @Mock
    private UserSessionRegistry sessionRegistry;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private AuthEventPublisher eventPublisher;

    @Mock
    private AuthMetricsRecorder authMetrics;

    @Test
    @DisplayName("유효한 refreshToken으로 새 accessToken + 새 refreshToken 발급 (rotation)")
    void refresh_success() {
        UUID userId = UUID.randomUUID();
        User user = User.create("test@example.com", "encodedPw", "홍길동");
        String oldRefreshToken = "valid-refresh-token";

        given(refreshTokenStore.findUserIdByToken(oldRefreshToken)).willReturn(Optional.of(userId));
        given(refreshTokenStore.invalidate(eq(oldRefreshToken), anyLong())).willReturn(true);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(tokenGenerator.generateAccessToken(user)).willReturn("new-jwt-token");
        given(tokenGenerator.accessTokenTtlSeconds()).willReturn(3600L);
        given(tokenProperties.refreshTokenTtlSeconds()).willReturn(2592000L);
        given(sessionProperties.inactivityTimeoutSeconds()).willReturn(604800L);

        RefreshResult result = refreshTokenService.refresh(new RefreshCommand(oldRefreshToken, "127.0.0.1", "Mozilla/5.0"));

        assertThat(result.accessToken()).isEqualTo("new-jwt-token");
        assertThat(result.expiresIn()).isEqualTo(3600L);
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotEqualTo(oldRefreshToken);

        // invalidate가 save보다 먼저 호출되어야 한다 (순서 검증)
        var ordered = inOrder(refreshTokenStore);
        ordered.verify(refreshTokenStore).invalidate(eq(oldRefreshToken), eq(2592000L));
        ordered.verify(refreshTokenStore).save(eq(result.refreshToken()), eq(userId), eq(2592000L));

        then(sessionRegistry).should().rotateSession(eq(userId), eq(oldRefreshToken), anyString(), eq(604800L));
        then(auditLogService).should().recordTokenRefresh(eq(userId), eq("test@example.com"), eq("127.0.0.1"), eq("Mozilla/5.0"));
        then(eventPublisher).should().publish(argThat(e -> e.eventType().equals("TokenRefreshed")));
    }

    @Test
    @DisplayName("동시 refresh 요청 시 두 번째 요청은 RefreshTokenRevokedException 발생 (레이스 조건 방어)")
    void refresh_concurrentRequest_secondThrowsRevoked() {
        String oldRefreshToken = "concurrent-token";

        given(refreshTokenStore.findUserIdByToken(oldRefreshToken)).willReturn(Optional.of(UUID.randomUUID()));
        given(userRepository.findById(any())).willReturn(Optional.of(
            User.create("test@example.com", "encodedPw", "홍길동")
        ));
        given(tokenProperties.refreshTokenTtlSeconds()).willReturn(2592000L);
        // invalidate가 false → 이미 다른 요청이 먼저 처리한 상황
        given(refreshTokenStore.invalidate(eq(oldRefreshToken), anyLong())).willReturn(false);

        assertThatThrownBy(() -> refreshTokenService.refresh(new RefreshCommand(oldRefreshToken, null, null)))
            .isInstanceOf(RefreshTokenRevokedException.class);

        then(auditLogService).should(never()).recordTokenRefresh(any(), any(), any(), any());
        then(eventPublisher).should(never()).publish(any());
    }

    @Test
    @DisplayName("폐기된 refreshToken이면 RefreshTokenRevokedException 발생")
    void refresh_revoked_throws() {
        String token = "revoked-token";

        given(refreshTokenStore.findUserIdByToken(token)).willReturn(Optional.empty());
        given(refreshTokenStore.isRevoked(token)).willReturn(true);

        assertThatThrownBy(() -> refreshTokenService.refresh(new RefreshCommand(token, null, null)))
            .isInstanceOf(RefreshTokenRevokedException.class);

        then(eventPublisher).should(never()).publish(any());
    }

    @Test
    @DisplayName("존재하지 않는 refreshToken이면 InvalidRefreshTokenException 발생")
    void refresh_notFound_throws() {
        String token = "unknown-token";
        given(refreshTokenStore.findUserIdByToken(token)).willReturn(Optional.empty());
        given(refreshTokenStore.isRevoked(token)).willReturn(false);

        assertThatThrownBy(() -> refreshTokenService.refresh(new RefreshCommand(token, null, null)))
            .isInstanceOf(InvalidRefreshTokenException.class);

        then(eventPublisher).should(never()).publish(any());
    }

    @Test
    @DisplayName("findUserIdByToken() 실패 시 DataAccessException이 전파된다")
    void refresh_findUserIdByToken_throwsDataAccessException() {
        given(refreshTokenStore.findUserIdByToken(anyString()))
            .willThrow(new org.springframework.dao.QueryTimeoutException("Redis timeout"));

        assertThatThrownBy(() -> refreshTokenService.refresh(new RefreshCommand("some-token", null, null)))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);

        then(eventPublisher).should(never()).publish(any());
    }

    @Test
    @DisplayName("invalidate() 실패 시 DataAccessException이 전파된다")
    void refresh_invalidate_throwsDataAccessException() {
        UUID userId = UUID.randomUUID();
        given(refreshTokenStore.findUserIdByToken("some-token")).willReturn(Optional.of(userId));
        given(userRepository.findById(userId)).willReturn(Optional.of(
            User.create("test@example.com", "encodedPw", "홍길동")
        ));
        given(tokenProperties.refreshTokenTtlSeconds()).willReturn(2592000L);
        given(refreshTokenStore.invalidate(eq("some-token"), anyLong()))
            .willThrow(new org.springframework.dao.QueryTimeoutException("Redis timeout"));

        assertThatThrownBy(() -> refreshTokenService.refresh(new RefreshCommand("some-token", null, null)))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);

        then(auditLogService).should(never()).recordTokenRefresh(any(), any(), any(), any());
        then(eventPublisher).should(never()).publish(any());
    }

    @Test
    @DisplayName("save() 실패 시 DataAccessException이 전파된다")
    void refresh_save_throwsDataAccessException() {
        UUID userId = UUID.randomUUID();
        User user = User.create("test@example.com", "encodedPw", "홍길동");
        given(refreshTokenStore.findUserIdByToken("some-token")).willReturn(Optional.of(userId));
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(tokenProperties.refreshTokenTtlSeconds()).willReturn(2592000L);
        given(refreshTokenStore.invalidate(eq("some-token"), anyLong())).willReturn(true);
        given(tokenGenerator.generateAccessToken(user)).willReturn("new-jwt-token");
        willThrow(new org.springframework.dao.QueryTimeoutException("Redis timeout"))
            .given(refreshTokenStore).save(anyString(), any(), anyLong());

        assertThatThrownBy(() -> refreshTokenService.refresh(new RefreshCommand("some-token", null, null)))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);

        then(sessionRegistry).should(never()).rotateSession(any(), any(), any(), anyLong());
        then(auditLogService).should(never()).recordTokenRefresh(any(), any(), any(), any());
        then(eventPublisher).should(never()).publish(any());
    }

}
