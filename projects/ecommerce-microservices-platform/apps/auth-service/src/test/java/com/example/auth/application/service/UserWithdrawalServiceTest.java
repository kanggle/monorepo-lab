package com.example.auth.application.service;

import com.example.auth.domain.entity.User;
import com.example.auth.domain.repository.AccessTokenBlocklist;
import com.example.auth.domain.repository.RefreshTokenStore;
import com.example.auth.domain.repository.UserRepository;
import com.example.auth.domain.repository.UserSessionRegistry;
import com.example.auth.domain.service.TokenProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserWithdrawalService 단위 테스트")
class UserWithdrawalServiceTest {

    @InjectMocks
    private UserWithdrawalService userWithdrawalService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    @Mock
    private AccessTokenBlocklist accessTokenBlocklist;

    @Mock
    private TokenProperties tokenProperties;

    @Mock
    private UserSessionRegistry sessionRegistry;

    @Mock
    private AuditLogService auditLogService;

    @Test
    @DisplayName("정상 탈퇴 처리 — 계정 비활성화, 토큰 폐기, 세션 삭제, 감사 로그 기록")
    void handleUserWithdrawal_validUser_deactivatesAndCleansUp() {
        UUID userId = UUID.randomUUID();
        User user = User.create("test@example.com", "encodedPassword", "TestUser");
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));
        given(tokenProperties.refreshTokenTtlSeconds()).willReturn(604800L);
        given(tokenProperties.accessTokenTtlSeconds()).willReturn(3600L);

        userWithdrawalService.handleUserWithdrawal(userId.toString());

        assertThat(user.isActive()).isFalse();
        then(userRepository).should().save(user);
        then(refreshTokenStore).should().invalidateAllByUserId(userId, 604800L);
        then(accessTokenBlocklist).should().blockByUserId(userId, 3600L);
        then(sessionRegistry).should().removeAllSessions(userId);
        then(auditLogService).should().recordAccountDeactivation(eq(userId), eq("test@example.com"));
    }

    @Test
    @DisplayName("존재하지 않는 사용자 — 경고 로그 후 정상 완료")
    void handleUserWithdrawal_userNotFound_skips() {
        UUID userId = UUID.randomUUID();
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        userWithdrawalService.handleUserWithdrawal(userId.toString());

        then(userRepository).should(never()).save(any());
        then(refreshTokenStore).should(never()).invalidateAllByUserId(any(), eq(0L));
        then(sessionRegistry).should(never()).removeAllSessions(any());
    }

    @Test
    @DisplayName("이미 비활성화된 사용자 — 중복 이벤트 멱등 처리")
    void handleUserWithdrawal_alreadyDeactivated_skips() {
        UUID userId = UUID.randomUUID();
        User user = User.create("test@example.com", "encodedPassword", "TestUser");
        user.deactivate();
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        userWithdrawalService.handleUserWithdrawal(userId.toString());

        then(userRepository).should(never()).save(any());
        then(refreshTokenStore).should(never()).invalidateAllByUserId(any(), eq(0L));
        then(sessionRegistry).should(never()).removeAllSessions(any());
    }

    @Test
    @DisplayName("잘못된 userId 형식 — 경고 로그 후 정상 완료")
    void handleUserWithdrawal_invalidUuidFormat_skips() {
        userWithdrawalService.handleUserWithdrawal("invalid-uuid");

        then(userRepository).should(never()).findById(any());
    }

    @Test
    @DisplayName("세션 삭제 Redis 실패 — fail-open으로 계정 비활성화는 완료")
    void handleUserWithdrawal_sessionRemovalFails_continuesGracefully() {
        UUID userId = UUID.randomUUID();
        User user = User.create("test@example.com", "encodedPassword", "TestUser");
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));
        given(tokenProperties.refreshTokenTtlSeconds()).willReturn(604800L);
        given(tokenProperties.accessTokenTtlSeconds()).willReturn(3600L);
        doThrow(new DataAccessException("Redis error") {}).when(sessionRegistry).removeAllSessions(userId);

        userWithdrawalService.handleUserWithdrawal(userId.toString());

        assertThat(user.isActive()).isFalse();
        then(userRepository).should().save(user);
        then(auditLogService).should().recordAccountDeactivation(eq(userId), eq("test@example.com"));
    }

    @Test
    @DisplayName("refresh token 폐기 실패 — fail-open으로 나머지 처리 계속 진행")
    void handleUserWithdrawal_refreshTokenInvalidationFails_continuesGracefully() {
        UUID userId = UUID.randomUUID();
        User user = User.create("test@example.com", "encodedPassword", "TestUser");
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));
        given(tokenProperties.refreshTokenTtlSeconds()).willReturn(604800L);
        given(tokenProperties.accessTokenTtlSeconds()).willReturn(3600L);
        doThrow(new DataAccessException("Redis error") {})
            .when(refreshTokenStore).invalidateAllByUserId(userId, 604800L);

        userWithdrawalService.handleUserWithdrawal(userId.toString());

        assertThat(user.isActive()).isFalse();
        then(accessTokenBlocklist).should().blockByUserId(userId, 3600L);
        then(sessionRegistry).should().removeAllSessions(userId);
        then(auditLogService).should().recordAccountDeactivation(eq(userId), eq("test@example.com"));
    }

    @Test
    @DisplayName("access token 차단 실패 — fail-open으로 나머지 처리 계속 진행")
    void handleUserWithdrawal_accessTokenBlockFails_continuesGracefully() {
        UUID userId = UUID.randomUUID();
        User user = User.create("test@example.com", "encodedPassword", "TestUser");
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));
        given(tokenProperties.refreshTokenTtlSeconds()).willReturn(604800L);
        given(tokenProperties.accessTokenTtlSeconds()).willReturn(3600L);
        doThrow(new DataAccessException("Redis error") {})
            .when(accessTokenBlocklist).blockByUserId(userId, 3600L);

        userWithdrawalService.handleUserWithdrawal(userId.toString());

        assertThat(user.isActive()).isFalse();
        then(refreshTokenStore).should().invalidateAllByUserId(userId, 604800L);
        then(sessionRegistry).should().removeAllSessions(userId);
        then(auditLogService).should().recordAccountDeactivation(eq(userId), eq("test@example.com"));
    }
}
