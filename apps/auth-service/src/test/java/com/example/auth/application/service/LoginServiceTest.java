package com.example.auth.application.service;

import com.example.auth.application.dto.LoginCommand;
import com.example.auth.application.dto.LoginResult;
import com.example.auth.application.exception.InvalidCredentialsException;
import com.example.auth.domain.entity.User;
import com.example.auth.domain.event.AuthEventPublisher;
import com.example.auth.domain.repository.RefreshTokenStore;
import com.example.auth.domain.repository.UserRepository;
import com.example.auth.domain.repository.UserSessionRegistry;
import com.example.auth.domain.repository.UserSessionRegistry.RegistrationResult;
import com.example.auth.domain.service.SessionProperties;
import com.example.auth.domain.service.TokenGenerator;
import com.example.auth.domain.service.TokenProperties;
import com.example.auth.domain.service.AuthMetricsRecorder;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import com.example.auth.domain.service.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoginService 단위 테스트")
class LoginServiceTest {

    @InjectMocks
    private LoginService loginService;

    private ListAppender<ILoggingEvent> listAppender;
    private Logger logbackLogger;

    @BeforeEach
    void setUpLogCapture() {
        logbackLogger = (Logger) LoggerFactory.getLogger(LoginService.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logbackLogger.addAppender(listAppender);
    }

    @AfterEach
    void tearDownLogCapture() {
        logbackLogger.detachAppender(listAppender);
    }

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    @Mock
    private TokenGenerator tokenGenerator;

    @Mock
    private PasswordEncoder passwordEncoder;

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
    @DisplayName("정상 로그인 - accessToken, refreshToken, expiresIn 반환")
    void login_success() {
        User user = User.create("test@example.com", "encodedPw", "홍길동");
        LoginCommand command = new LoginCommand("test@example.com", "password1!", "127.0.0.1", "Mozilla/5.0");

        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password1!", "encodedPw")).willReturn(true);
        given(tokenGenerator.generateAccessToken(user)).willReturn("jwt-token");
        given(tokenGenerator.accessTokenTtlSeconds()).willReturn(3600L);
        given(tokenProperties.refreshTokenTtlSeconds()).willReturn(2592000L);
        given(sessionProperties.inactivityTimeoutSeconds()).willReturn(604800L);
        willDoNothing().given(refreshTokenStore).save(anyString(), any(), anyLong());
        given(sessionRegistry.registerSession(eq(user.getId()), anyString(), eq(604800L)))
            .willReturn(new RegistrationResult("new-hash", null));

        LoginResult result = loginService.login(command);

        assertThat(result.accessToken()).isEqualTo("jwt-token");
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.expiresIn()).isEqualTo(3600L);
        then(refreshTokenStore).should().save(anyString(), eq(user.getId()), eq(2592000L));
        then(sessionRegistry).should().registerSession(eq(user.getId()), anyString(), eq(604800L));
        then(auditLogService).should().recordLoginSuccess(eq(user.getId()), eq(user.getEmail().value()), eq("127.0.0.1"), eq("Mozilla/5.0"));
        then(eventPublisher).should().publish(argThat(e -> e.eventType().equals("UserLoggedIn")));
    }

    @Test
    @DisplayName("존재하지 않는 이메일이면 InvalidCredentialsException 발생 (timing attack 방지를 위해 passwordEncoder 호출됨)")
    void login_emailNotFound_throws() {
        given(userRepository.findByEmail(anyString())).willReturn(Optional.empty());
        given(passwordEncoder.matches(anyString(), anyString())).willReturn(false);

        assertThatThrownBy(() -> loginService.login(new LoginCommand("notfound@example.com", "pw", null, null)))
            .isInstanceOf(InvalidCredentialsException.class);

        then(passwordEncoder).should().matches(anyString(), anyString());
        then(auditLogService).should().recordLoginFailure(eq("notfound@example.com"), isNull(), isNull(), eq("INVALID_CREDENTIALS"));
        then(eventPublisher).should().publish(argThat(e -> e.eventType().equals("LoginFailed")));
    }

    @Test
    @DisplayName("대문자 이메일로 로그인해도 정규화 후 사용자를 찾는다")
    void login_uppercaseEmail_succeeds() {
        User user = User.create("test@example.com", "encodedPw", "홍길동");
        LoginCommand command = new LoginCommand("TEST@EXAMPLE.COM", "password1!", null, null);

        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password1!", "encodedPw")).willReturn(true);
        given(tokenGenerator.generateAccessToken(user)).willReturn("jwt-token");
        given(tokenGenerator.accessTokenTtlSeconds()).willReturn(3600L);
        given(tokenProperties.refreshTokenTtlSeconds()).willReturn(2592000L);
        given(sessionProperties.inactivityTimeoutSeconds()).willReturn(604800L);
        willDoNothing().given(refreshTokenStore).save(anyString(), any(), anyLong());
        given(sessionRegistry.registerSession(eq(user.getId()), anyString(), eq(604800L)))
            .willReturn(new RegistrationResult("new-hash", null));

        LoginResult result = loginService.login(command);

        assertThat(result.accessToken()).isEqualTo("jwt-token");
    }

    @Test
    @DisplayName("비밀번호 불일치이면 InvalidCredentialsException 발생")
    void login_wrongPassword_throws() {
        User user = User.create("test@example.com", "encodedPw", "홍길동");
        given(userRepository.findByEmail(anyString())).willReturn(Optional.of(user));
        given(passwordEncoder.matches(anyString(), anyString())).willReturn(false);

        assertThatThrownBy(() -> loginService.login(new LoginCommand("test@example.com", "wrongpw", null, null)))
            .isInstanceOf(InvalidCredentialsException.class);

        then(auditLogService).should().recordLoginFailure(eq("test@example.com"), isNull(), isNull(), eq("INVALID_CREDENTIALS"));
        then(eventPublisher).should().publish(argThat(e -> e.eventType().equals("LoginFailed")));
    }

    @Test
    @DisplayName("대문자 이메일로 로그인 실패 시 감사 로그에 정규화된 이메일이 기록된다")
    void login_uppercaseEmail_failure_recordsNormalizedEmail() {
        given(userRepository.findByEmail("upper@example.com")).willReturn(Optional.empty());
        given(passwordEncoder.matches(anyString(), anyString())).willReturn(false);

        assertThatThrownBy(() -> loginService.login(new LoginCommand("UPPER@EXAMPLE.COM", "pw", null, null)))
            .isInstanceOf(InvalidCredentialsException.class);

        then(auditLogService).should().recordLoginFailure(eq("upper@example.com"), isNull(), isNull(), eq("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("세션 한도 초과 시 SessionLimitExceeded 이벤트가 발행되고 UserLoggedIn 이벤트도 발행된다")
    void login_sessionLimitExceeded_publishesEvent() {
        User user = User.create("test@example.com", "encodedPw", "홍길동");
        LoginCommand command = new LoginCommand("test@example.com", "password1!", "127.0.0.1", "Mozilla/5.0");
        String evictedHash = "evicted-session-hash";

        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password1!", "encodedPw")).willReturn(true);
        given(tokenGenerator.generateAccessToken(user)).willReturn("jwt-token");
        given(tokenGenerator.accessTokenTtlSeconds()).willReturn(3600L);
        given(tokenProperties.refreshTokenTtlSeconds()).willReturn(2592000L);
        given(sessionProperties.inactivityTimeoutSeconds()).willReturn(604800L);
        willDoNothing().given(refreshTokenStore).save(anyString(), any(), anyLong());
        given(sessionRegistry.registerSession(eq(user.getId()), anyString(), eq(604800L)))
            .willReturn(new RegistrationResult("new-session-hash", evictedHash));

        loginService.login(command);

        then(eventPublisher).should().publish(argThat(e -> e.eventType().equals("SessionLimitExceeded")));
        then(eventPublisher).should().publish(argThat(e -> e.eventType().equals("UserLoggedIn")));
    }

    @Test
    @DisplayName("비활성 계정 로그인 실패 시 로그에 이메일이 포함되지 않는다")
    void login_deactivatedAccount_logDoesNotContainEmail() {
        String email = "deactivated@example.com";
        User user = User.create(email, "encodedPw", "홍길동");
        user.deactivate();
        given(userRepository.findByEmail(email)).willReturn(Optional.of(user));
        given(passwordEncoder.matches(anyString(), anyString())).willReturn(true);

        assertThatThrownBy(() -> loginService.login(new LoginCommand(email, "password1!", null, null)))
            .isInstanceOf(InvalidCredentialsException.class);

        assertThat(listAppender.list)
            .filteredOn(e -> e.getFormattedMessage().contains("account deactivated"))
            .allSatisfy(e -> assertThat(e.getFormattedMessage()).doesNotContain(email));
    }

    @Test
    @DisplayName("잘못된 비밀번호 로그인 실패 시 로그에 이메일이 포함되지 않는다")
    void login_invalidCredentials_logDoesNotContainEmail() {
        String email = "test@example.com";
        given(userRepository.findByEmail(email)).willReturn(Optional.empty());
        given(passwordEncoder.matches(anyString(), anyString())).willReturn(false);

        assertThatThrownBy(() -> loginService.login(new LoginCommand(email, "wrongpw", null, null)))
            .isInstanceOf(InvalidCredentialsException.class);

        assertThat(listAppender.list)
            .allSatisfy(e -> assertThat(e.getFormattedMessage()).doesNotContain(email));
    }

    @Test
    @DisplayName("refreshTokenStore.save() 실패 시 DataAccessException이 전파된다")
    void login_refreshTokenSaveFails_propagatesException() {
        User user = User.create("test@example.com", "encodedPw", "홍길동");
        LoginCommand command = new LoginCommand("test@example.com", "password1!", null, null);

        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password1!", "encodedPw")).willReturn(true);
        given(tokenGenerator.generateAccessToken(user)).willReturn("jwt-token");
        given(tokenProperties.refreshTokenTtlSeconds()).willReturn(2592000L);
        willThrow(new org.springframework.dao.QueryTimeoutException("Redis timeout"))
            .given(refreshTokenStore).save(anyString(), any(), anyLong());

        assertThatThrownBy(() -> loginService.login(command))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);

        then(auditLogService).should(never()).recordLoginSuccess(any(), any(), any(), any());
        then(eventPublisher).should(never()).publish(any());
    }
}
