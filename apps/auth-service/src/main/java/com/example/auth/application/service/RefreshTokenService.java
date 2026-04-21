package com.example.auth.application.service;

import com.example.auth.application.dto.RefreshCommand;
import com.example.auth.application.dto.RefreshResult;
import com.example.auth.application.exception.InvalidRefreshTokenException;
import com.example.auth.application.exception.RefreshTokenRevokedException;
import com.example.auth.domain.entity.User;
import com.example.auth.domain.event.AuthEvent;
import com.example.auth.domain.event.AuthEventPublisher;
import com.example.auth.domain.event.TokenRefreshed;
import com.example.auth.domain.repository.RefreshTokenStore;
import com.example.auth.domain.repository.UserRepository;
import com.example.auth.domain.repository.UserSessionRegistry;
import com.example.auth.domain.service.SessionProperties;
import com.example.auth.domain.service.AuthMetricsRecorder;
import com.example.auth.domain.service.TokenGenerator;
import com.example.auth.domain.service.TokenProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefreshTokenService {

    private final RefreshTokenStore refreshTokenStore;
    private final UserRepository userRepository;
    private final TokenGenerator tokenGenerator;
    private final TokenProperties tokenProperties;
    private final SessionProperties sessionProperties;
    private final UserSessionRegistry sessionRegistry;
    private final AuditLogService auditLogService;
    private final AuthEventPublisher eventPublisher;
    private final AuthMetricsRecorder authMetrics;

    public RefreshResult refresh(RefreshCommand command) {
        String oldRefreshToken = command.refreshToken();

        UUID userId = resolveTokenOwner(oldRefreshToken);
        User user = userRepository.findById(userId)
            .orElseThrow(InvalidRefreshTokenException::new);

        invalidateOldToken(oldRefreshToken, userId);

        String[] newTokens = issueNewTokens(user, userId, oldRefreshToken);
        String newAccessToken = newTokens[0];
        String newRefreshToken = newTokens[1];

        handleRefreshSuccess(userId, user.getEmail().value(), newRefreshToken, command);

        return new RefreshResult(newAccessToken, newRefreshToken, tokenGenerator.accessTokenTtlSeconds());
    }

    private UUID resolveTokenOwner(String oldRefreshToken) {
        try {
            return refreshTokenStore.findUserIdByToken(oldRefreshToken)
                .orElseThrow(() -> {
                    authMetrics.incrementTokenRefreshFailure();
                    if (refreshTokenStore.isRevoked(oldRefreshToken)) {
                        return new RefreshTokenRevokedException();
                    }
                    return new InvalidRefreshTokenException();
                });
        } catch (DataAccessException e) {
            log.error("Token refresh failed: Redis error during token lookup", e);
            throw e;
        }
    }

    private void invalidateOldToken(String oldRefreshToken, UUID userId) {
        // invalidate 먼저 수행: DEL 반환값으로 레이스 조건 감지
        // 동시 요청 시 두 번째 요청은 DEL 0 → false → RevokedException
        boolean invalidated;
        try {
            invalidated = refreshTokenStore.invalidate(oldRefreshToken, tokenProperties.refreshTokenTtlSeconds());
        } catch (DataAccessException e) {
            log.error("Token refresh failed: Redis error during invalidation, userId={}", userId, e);
            throw e;
        }
        if (!invalidated) {
            // invalidate()가 false이면 토큰이 이미 삭제됨.
            // 로그아웃으로 revoke된 경우와 동시 refresh 요청(레이스 조건)을 구분하여 로깅.
            try {
                if (refreshTokenStore.isRevoked(oldRefreshToken)) {
                    log.warn("Token refresh denied: token already revoked (logged out), userId={}", userId);
                } else {
                    log.warn("Token refresh denied: concurrent request detected (race condition), userId={}", userId);
                }
            } catch (DataAccessException e) {
                log.warn("Token refresh denied: could not determine revocation cause, userId={}", userId);
            }
            authMetrics.incrementTokenRefreshFailure();
            throw new RefreshTokenRevokedException();
        }
    }

    private String[] issueNewTokens(User user, UUID userId, String oldRefreshToken) {
        String newAccessToken = tokenGenerator.generateAccessToken(user);
        String newRefreshToken = UUID.randomUUID().toString();
        try {
            refreshTokenStore.save(newRefreshToken, userId, tokenProperties.refreshTokenTtlSeconds());
        } catch (DataAccessException e) {
            // 기존 토큰은 이미 무효화됨. 새 토큰 저장 실패 → 재로그인 필요 안내를 위해 예외 전파.
            log.error("Token refresh failed: Redis error during new token save, userId={}", userId, e);
            throw e;
        }

        // 세션 레지스트리 갱신 — fail-open: refresh token 저장 성공 후이므로 실패해도 로그만 남김
        try {
            sessionRegistry.rotateSession(userId, oldRefreshToken, newRefreshToken,
                sessionProperties.inactivityTimeoutSeconds());
        } catch (DataAccessException e) {
            log.error("Session registry failed during token rotation, userId={}", userId, e);
        }

        return new String[]{newAccessToken, newRefreshToken};
    }

    private void handleRefreshSuccess(UUID userId, String email, String newRefreshToken, RefreshCommand command) {
        log.info("Token rotated: userId={}", userId);
        authMetrics.incrementTokenRefreshSuccess();
        auditLogService.recordTokenRefresh(userId, email, command.ipAddress(), command.userAgent());
        eventPublisher.publish(AuthEvent.of(new TokenRefreshed(userId, newRefreshToken)));
    }
}
