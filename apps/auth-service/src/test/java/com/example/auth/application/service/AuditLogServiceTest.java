package com.example.auth.application.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.auth.domain.entity.AuditEventType;
import com.example.auth.domain.entity.AuditLog;
import com.example.auth.domain.entity.AuditResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditLogService 단위 테스트")
class AuditLogServiceTest {

    @InjectMocks
    private AuditLogService auditLogService;

    @Mock
    private AuditLogWriter auditLogWriter;

    private ListAppender<ILoggingEvent> listAppender;
    private Logger logbackLogger;

    @BeforeEach
    void setUpLogCapture() {
        logbackLogger = (Logger) LoggerFactory.getLogger(AuditLogService.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logbackLogger.addAppender(listAppender);
    }

    @AfterEach
    void tearDownLogCapture() {
        logbackLogger.detachAppender(listAppender);
    }

    @Test
    @DisplayName("recordSignup - SIGNUP 이벤트가 저장된다")
    void recordSignup_savesSignupEvent() {
        UUID userId = UUID.randomUUID();

        auditLogService.recordSignup(userId, "user@example.com", "127.0.0.1", "Mozilla/5.0");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        then(auditLogWriter).should().save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getEmail()).isEqualTo("user@example.com");
        assertThat(saved.getEventType()).isEqualTo(AuditEventType.SIGNUP);
        assertThat(saved.getResult()).isEqualTo(AuditResult.SUCCESS);
        assertThat(saved.getIpAddress()).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("recordLoginSuccess - LOGIN_SUCCESS 이벤트가 저장된다")
    void recordLoginSuccess_savesLoginSuccessEvent() {
        UUID userId = UUID.randomUUID();

        auditLogService.recordLoginSuccess(userId, "user@example.com", "10.0.0.1", "curl/7.0");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        then(auditLogWriter).should().save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo(AuditEventType.LOGIN_SUCCESS);
        assertThat(saved.getResult()).isEqualTo(AuditResult.SUCCESS);
        assertThat(saved.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("recordLoginFailure - LOGIN_FAILURE 이벤트가 저장되고 userId는 null이다")
    void recordLoginFailure_savesLoginFailureEvent() {
        auditLogService.recordLoginFailure("bad@example.com", "192.168.0.1", null, "INVALID_CREDENTIALS");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        then(auditLogWriter).should().save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getUserId()).isNull();
        assertThat(saved.getEventType()).isEqualTo(AuditEventType.LOGIN_FAILURE);
        assertThat(saved.getResult()).isEqualTo(AuditResult.FAILURE);
        assertThat(saved.getFailureReason()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    @DisplayName("recordTokenRefresh - TOKEN_REFRESH 이벤트가 저장된다")
    void recordTokenRefresh_savesTokenRefreshEvent() {
        UUID userId = UUID.randomUUID();

        auditLogService.recordTokenRefresh(userId, "user@example.com", "127.0.0.1", null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        then(auditLogWriter).should().save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(AuditEventType.TOKEN_REFRESH);
    }

    @Test
    @DisplayName("recordLogout - LOGOUT 이벤트가 저장된다")
    void recordLogout_savesLogoutEvent() {
        UUID userId = UUID.randomUUID();

        auditLogService.recordLogout(userId, "user@example.com", "127.0.0.1", null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        then(auditLogWriter).should().save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(AuditEventType.LOGOUT);
    }

    @Test
    @DisplayName("auditLogWriter.save() 실패 시 예외를 삼키고 정상 종료된다 (비차단)")
    void save_writerThrows_doesNotPropagate() {
        willThrow(new RuntimeException("DB error")).given(auditLogWriter).save(any());

        assertThatCode(() ->
            auditLogService.recordLoginSuccess(UUID.randomUUID(), "user@example.com", null, null)
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("auditLogWriter.save() 실패 시 에러 로그에 이메일이 포함되지 않는다")
    void save_writerThrows_logDoesNotContainEmail() {
        String email = "sensitive@example.com";
        willThrow(new RuntimeException("DB error")).given(auditLogWriter).save(any());

        auditLogService.recordLoginSuccess(UUID.randomUUID(), email, null, null);

        assertThat(listAppender.list)
            .isNotEmpty()
            .allSatisfy(e -> assertThat(e.getFormattedMessage()).doesNotContain(email));
    }

    @Test
    @DisplayName("auditLogWriter.save()가 UnexpectedRollbackException을 던져도 예외를 삼킨다")
    void save_unexpectedRollbackException_doesNotPropagate() {
        willThrow(new org.springframework.transaction.UnexpectedRollbackException("rollback-only"))
            .given(auditLogWriter).save(any());

        assertThatCode(() ->
            auditLogService.recordSignup(UUID.randomUUID(), "user@example.com", null, null)
        ).doesNotThrowAnyException();
    }
}
