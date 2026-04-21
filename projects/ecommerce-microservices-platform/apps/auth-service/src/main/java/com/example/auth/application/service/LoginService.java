package com.example.auth.application.service;

import com.example.auth.application.dto.LoginCommand;
import com.example.auth.application.dto.LoginResult;
import com.example.auth.application.exception.InvalidCredentialsException;
import com.example.auth.domain.entity.User;
import com.example.auth.domain.event.AuthEvent;
import com.example.auth.domain.event.AuthEventPublisher;
import com.example.auth.domain.event.LoginFailed;
import com.example.auth.domain.event.SessionLimitExceeded;
import com.example.auth.domain.event.UserLoggedIn;
import com.example.auth.domain.repository.RefreshTokenStore;
import com.example.auth.domain.repository.UserRepository;
import com.example.auth.domain.repository.UserSessionRegistry;
import com.example.auth.domain.service.SessionProperties;
import com.example.auth.domain.service.AuthMetricsRecorder;
import com.example.auth.domain.service.TokenGenerator;
import com.example.auth.domain.service.TokenProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import com.example.auth.domain.service.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoginService {

    private final UserRepository userRepository;
    private final RefreshTokenStore refreshTokenStore;
    private final TokenGenerator tokenGenerator;
    private final PasswordEncoder passwordEncoder;
    private final TokenProperties tokenProperties;
    private final SessionProperties sessionProperties;
    private final UserSessionRegistry sessionRegistry;
    private final AuditLogService auditLogService;
    private final AuthEventPublisher eventPublisher;
    private final AuthMetricsRecorder authMetrics;

    // 이메일 존재 여부 노출을 막기 위한 더미 해시.
    // @PostConstruct에서 주입된 PasswordEncoder로 재생성하여 encoder의
    // 실제 work factor(BCrypt cost 등)와 일치시킨다.
    // 기본값은 BCrypt $2a$10$ 형식의 유효한 해시로,
    // @PostConstruct 미호출 환경(단위 테스트 등)에서의 NPE를 방지한다.
    private String dummyHash = "$2a$10$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ01234";

    @PostConstruct
    void initDummyHash() {
        this.dummyHash = passwordEncoder.encode("__dummy_password_for_timing_safety__");
    }

    public LoginResult login(LoginCommand command) {
        String normalizedEmail = command.email().toLowerCase().trim();
        User user = userRepository.findByEmail(normalizedEmail).orElse(null);

        String hashToVerify = (user != null) ? user.getPasswordHash() : dummyHash;
        boolean passwordMatches = passwordEncoder.matches(command.password(), hashToVerify);

        if (user != null && !user.isActive()) {
            handleLoginFailure(normalizedEmail, command, "account deactivated", "account_deactivated", "ACCOUNT_DEACTIVATED", user.getId());
        }

        if (user == null || !passwordMatches) {
            handleLoginFailure(normalizedEmail, command, "invalid credentials", "invalid_credentials", "INVALID_CREDENTIALS", null);
        }

        String accessToken = tokenGenerator.generateAccessToken(user);
        String refreshToken = UUID.randomUUID().toString();
        try {
            refreshTokenStore.save(refreshToken, user.getId(), tokenProperties.refreshTokenTtlSeconds());
        } catch (DataAccessException e) {
            log.error("Login failed: Redis error during refresh token save, userId={}", user.getId(), e);
            throw e;
        }

        // 세션 등록 — fail-open: Redis 장애 시 로그인은 허용하고 에러만 로깅
        UserSessionRegistry.RegistrationResult sessionResult = null;
        try {
            sessionResult = sessionRegistry.registerSession(
                user.getId(), refreshToken, sessionProperties.inactivityTimeoutSeconds());
        } catch (DataAccessException e) {
            log.error("Session registry failed during login, userId={}", user.getId(), e);
        }

        handleLoginSuccess(user, command, sessionResult, accessToken);

        return new LoginResult(accessToken, refreshToken, tokenGenerator.accessTokenTtlSeconds());
    }

    private void handleLoginFailure(String normalizedEmail, LoginCommand command,
                                    String logMessage, String metricsReason,
                                    String auditReason, Object userId) {
        if (userId != null) {
            log.warn("Login attempt failed: {}, userId={}", logMessage, userId);
        } else {
            log.warn("Login attempt failed: {}", logMessage);
        }
        authMetrics.incrementLoginFailure(metricsReason);
        auditLogService.recordLoginFailure(
            normalizedEmail, command.ipAddress(), command.userAgent(), auditReason);
        eventPublisher.publish(
            AuthEvent.of(new LoginFailed(normalizedEmail, command.ipAddress(), auditReason)));
        throw new InvalidCredentialsException();
    }

    private void handleLoginSuccess(User user, LoginCommand command,
                                    UserSessionRegistry.RegistrationResult sessionResult,
                                    String accessToken) {
        log.info("Login succeeded: userId={}", user.getId());
        authMetrics.incrementLoginSuccess();
        if (sessionResult != null && sessionResult.evictedSessionId() != null) {
            log.info("Session limit exceeded: oldest session evicted, userId={}", user.getId());
            authMetrics.incrementSessionEviction();
            eventPublisher.publish(
                AuthEvent.of(new SessionLimitExceeded(
                    user.getId(), sessionResult.evictedSessionId(), sessionResult.newSessionId())));
        }
        auditLogService.recordLoginSuccess(
            user.getId(), user.getEmail().value(), command.ipAddress(), command.userAgent());
        eventPublisher.publish(
            AuthEvent.of(new UserLoggedIn(user.getId(), user.getEmail().value(), command.ipAddress(), command.userAgent())));
    }
}
