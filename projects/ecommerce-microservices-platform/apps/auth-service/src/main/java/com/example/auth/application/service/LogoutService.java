package com.example.auth.application.service;

import com.example.auth.application.dto.LogoutCommand;
import com.example.auth.application.exception.InvalidRefreshTokenException;
import com.example.auth.domain.event.AuthEvent;
import com.example.auth.domain.event.AuthEventPublisher;
import com.example.auth.domain.event.UserLoggedOut;
import com.example.auth.domain.repository.AccessTokenBlocklist;
import com.example.auth.domain.repository.RefreshTokenStore;
import com.example.auth.domain.repository.UserSessionRegistry;
import com.example.auth.domain.service.TokenProperties;
import com.example.auth.domain.service.AuthMetricsRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogoutService {

    private final RefreshTokenStore refreshTokenStore;
    private final TokenProperties tokenProperties;
    private final AccessTokenBlocklist accessTokenBlocklist;
    private final UserSessionRegistry sessionRegistry;
    private final AuditLogService auditLogService;
    private final AuthEventPublisher eventPublisher;
    private final AuthMetricsRecorder authMetrics;

    public void logout(LogoutCommand command) {
        UUID tokenOwner;
        try {
            tokenOwner = refreshTokenStore.findUserIdByToken(command.refreshToken())
                .orElseThrow(InvalidRefreshTokenException::new);
        } catch (DataAccessException e) {
            log.error("Logout failed: Redis error during refresh token lookup", e);
            throw e;
        }

        if (!tokenOwner.equals(command.userId())) {
            throw new InvalidRefreshTokenException();
        }

        try {
            refreshTokenStore.invalidate(command.refreshToken(), tokenProperties.refreshTokenTtlSeconds());
        } catch (DataAccessException e) {
            log.error("Logout failed: Redis error during refresh token invalidation, userId={}", command.userId(), e);
            throw e;
        }

        // 세션 레지스트리 제거 — fail-open: refresh token은 이미 무효화됨
        try {
            sessionRegistry.removeSession(command.userId(), command.refreshToken());
        } catch (DataAccessException e) {
            log.error("Session registry removal failed after refresh token invalidated, userId={}", command.userId(), e);
        }

        if (command.accessToken() != null) {
            try {
                accessTokenBlocklist.block(command.accessToken(), tokenProperties.accessTokenTtlSeconds());
            } catch (DataAccessException e) {
                // refresh token은 이미 무효화됨. access token blocklist 실패는 로그만 남기고 통과.
                log.error("Access token blocklist failed after refresh token invalidated, userId={}", command.userId(), e);
            }
        }

        log.info("Logout succeeded: userId={}", command.userId());
        authMetrics.incrementLogout();
        auditLogService.recordLogout(command.userId(), command.email(), command.ipAddress(), command.userAgent());
        eventPublisher.publish(AuthEvent.of(new UserLoggedOut(command.userId(), command.refreshToken())));
    }
}
