package com.example.auth.application.service;

import com.example.auth.application.dto.SignupCommand;
import com.example.auth.application.dto.SignupResult;
import com.example.auth.application.exception.EmailAlreadyExistsException;
import com.example.auth.domain.entity.User;
import com.example.auth.domain.event.AuthEventPublisher;
import com.example.auth.domain.repository.UserRepository;
import com.example.auth.domain.service.AuthMetricsRecorder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.example.auth.domain.service.PasswordEncoder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SignupService 단위 테스트")
class SignupServiceTest {

    @InjectMocks
    private SignupService signupService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private AuthEventPublisher eventPublisher;

    @Mock
    private AuthMetricsRecorder authMetrics;

    @Test
    @DisplayName("정상 회원가입 - 저장된 사용자 정보를 반환한다")
    void signup_success() {
        SignupCommand command = new SignupCommand("test@example.com", "password1!", "홍길동", "127.0.0.1", "Mozilla/5.0");

        given(userRepository.existsByEmail(anyString())).willReturn(false);
        given(passwordEncoder.encode(anyString())).willReturn("encodedPw");
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        SignupResult result = signupService.signup(command);

        assertThat(result.email()).isEqualTo("test@example.com");
        assertThat(result.name()).isEqualTo("홍길동");
        assertThat(result.userId()).isNotNull();
        assertThat(result.createdAt()).isInstanceOf(Instant.class);
        then(userRepository).should().save(any(User.class));
        then(auditLogService).should().recordSignup(any(), eq("test@example.com"), eq("127.0.0.1"), eq("Mozilla/5.0"));
        // 트랜잭션 동기화가 없는 단위 테스트 환경에서는 즉시 발행
        then(eventPublisher).should().publish(argThat(e ->
            e.eventType().equals("UserSignedUp") && e.source().equals("auth-service")));
    }

    @Test
    @DisplayName("이미 존재하는 이메일이면 EmailAlreadyExistsException 발생")
    void signup_duplicateEmail_throws() {
        SignupCommand command = new SignupCommand("test@example.com", "password1!", "홍길동", null, null);
        given(userRepository.existsByEmail("test@example.com")).willReturn(true);

        assertThatThrownBy(() -> signupService.signup(command))
            .isInstanceOf(EmailAlreadyExistsException.class);

        then(userRepository).should(never()).save(any());
        then(auditLogService).should(never()).recordSignup(any(), any(), any(), any());
        then(eventPublisher).should(never()).publish(any());
    }

    @Test
    @DisplayName("대문자 이메일 입력 시 정규화 후 중복 체크한다")
    void signup_uppercaseEmailNormalized_duplicateCheck() {
        SignupCommand command = new SignupCommand("TEST@EXAMPLE.COM", "password1!", "홍길동", null, null);
        given(userRepository.existsByEmail("test@example.com")).willReturn(true);

        assertThatThrownBy(() -> signupService.signup(command))
            .isInstanceOf(EmailAlreadyExistsException.class);

        then(userRepository).should().existsByEmail("test@example.com");
        then(userRepository).should(never()).save(any());
        then(eventPublisher).should(never()).publish(any());
    }

    @Test
    @DisplayName("existsByEmail() 실패 시 DataAccessException이 전파된다")
    void signup_existsByEmail_throwsDataAccessException() {
        SignupCommand command = new SignupCommand("test@example.com", "password1!", "홍길동", null, null);
        given(userRepository.existsByEmail(anyString()))
            .willThrow(new org.springframework.dao.QueryTimeoutException("DB timeout"));

        assertThatThrownBy(() -> signupService.signup(command))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);

        then(userRepository).should(never()).save(any());
        then(eventPublisher).should(never()).publish(any());
    }

    @Test
    @DisplayName("save() 실패 시 DataAccessException이 전파된다")
    void signup_save_throwsDataAccessException() {
        SignupCommand command = new SignupCommand("test@example.com", "password1!", "홍길동", null, null);
        given(userRepository.existsByEmail(anyString())).willReturn(false);
        given(passwordEncoder.encode(anyString())).willReturn("encodedPw");
        given(userRepository.save(any(User.class)))
            .willThrow(new org.springframework.dao.QueryTimeoutException("DB timeout"));

        assertThatThrownBy(() -> signupService.signup(command))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);

        then(auditLogService).should(never()).recordSignup(any(), any(), any(), any());
        then(eventPublisher).should(never()).publish(any());
    }
}
