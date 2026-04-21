package com.example.auth.application.service;

import com.example.auth.domain.entity.User;
import com.example.auth.domain.repository.AccessTokenBlocklist;
import com.example.auth.domain.repository.RefreshTokenStore;
import com.example.auth.domain.repository.UserRepository;
import com.example.auth.domain.repository.UserSessionRegistry;
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
public class UserWithdrawalService {

    private final UserRepository userRepository;
    private final RefreshTokenStore refreshTokenStore;
    private final AccessTokenBlocklist accessTokenBlocklist;
    private final TokenProperties tokenProperties;
    private final UserSessionRegistry sessionRegistry;
    private final AuditLogService auditLogService;

    @Transactional
    public void handleUserWithdrawal(String userIdStr) {
        UUID userId;
        try {
            userId = UUID.fromString(userIdStr);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid userId format in UserWithdrawn event: {}", userIdStr);
            return;
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("User not found for UserWithdrawn event, skipping: userId={}", userId);
            return;
        }

        if (!user.isActive()) {
            log.info("User already deactivated, skipping: userId={}", userId);
            return;
        }

        // 1. 계정 비활성화 (DB)
        user.deactivate();
        userRepository.save(user);
        log.info("User account deactivated: userId={}", userId);

        // 2. 모든 refresh token 폐기 (Redis) — fail-open
        try {
            refreshTokenStore.invalidateAllByUserId(userId, tokenProperties.refreshTokenTtlSeconds());
        } catch (DataAccessException e) {
            log.error("Refresh token invalidation failed for withdrawn user: userId={}", userId, e);
        }

        // 3. access token userId 차단 등록 (Redis) — fail-open
        try {
            accessTokenBlocklist.blockByUserId(userId, tokenProperties.accessTokenTtlSeconds());
            log.info("Access tokens blocked by userId for withdrawn user: userId={}", userId);
        } catch (DataAccessException e) {
            log.error("Access token blocklist failed for withdrawn user: userId={}", userId, e);
        }

        // 4. 모든 세션 삭제 (Redis) — fail-open
        try {
            sessionRegistry.removeAllSessions(userId);
            log.info("All sessions removed for withdrawn user: userId={}", userId);
        } catch (DataAccessException e) {
            log.error("Session removal failed for withdrawn user: userId={}", userId, e);
        }

        // 5. 감사 로그 기록
        auditLogService.recordAccountDeactivation(userId, user.getEmail().value());
    }
}
