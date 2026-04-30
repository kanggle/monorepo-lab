package com.example.auth.infrastructure.email;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Unit tests for {@link LoggingEmailSender} (TASK-BE-242).
 *
 * <p>Mirrors the {@code LoggingEmailVerificationNotifier} pattern in
 * {@code account-service} (TASK-BE-236) — Logback {@link ListAppender}
 * captures emitted log events so we can assert on:
 * <ul>
 *   <li>that the call completes without throwing,</li>
 *   <li>that an INFO log line is actually produced (the bug-class TASK-BE-242
 *       is fixing — silently missing sender bean),</li>
 *   <li>that the password reset token is never emitted (R4),</li>
 *   <li>that the recipient email is masked before logging (R4).</li>
 * </ul>
 */
@DisplayName("LoggingEmailSender 단위 테스트")
class LoggingEmailSenderTest {

    private LoggingEmailSender sender;
    private Logger senderLogger;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        sender = new LoggingEmailSender();
        senderLogger = (Logger) LoggerFactory.getLogger(LoggingEmailSender.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        senderLogger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        senderLogger.detachAppender(listAppender);
    }

    @Test
    @DisplayName("sendPasswordResetEmail — 정상 이메일 → 예외 없이 완료")
    void sendPasswordResetEmail_validEmail_noException() {
        assertThatNoException().isThrownBy(() ->
                sender.sendPasswordResetEmail("alice@example.com", "reset-token-xyz"));
    }

    @Test
    @DisplayName("sendPasswordResetEmail — INFO 로그 라인이 1개 이상 출력된다 (bean 미등록 회귀 방지)")
    void sendPasswordResetEmail_emitsAtLeastOneInfoLogLine() {
        sender.sendPasswordResetEmail("alice@example.com", "some-reset-token");

        assertThat(listAppender.list)
                .as("LoggingEmailSender must produce a log line per call — "
                        + "missing log output would mask the very bug TASK-BE-242 fixes")
                .isNotEmpty();
        assertThat(listAppender.list)
                .anySatisfy(event -> {
                    assertThat(event.getLevel().toString()).isEqualTo("INFO");
                    assertThat(event.getFormattedMessage()).contains("Password reset email queued");
                });
    }

    @Test
    @DisplayName("sendPasswordResetEmail — 리셋 토큰이 로그에 출력되지 않음 (R4 보안 규칙)")
    void sendPasswordResetEmail_resetTokenNotLogged() {
        sender.sendPasswordResetEmail("alice@example.com", "secret-reset-token-abc");

        String logOutput = capturedLogOutput();
        assertThat(logOutput).doesNotContain("secret-reset-token-abc");
    }

    @Test
    @DisplayName("sendPasswordResetEmail — 이메일 첫 글자 + *** + 도메인 형태로 마스킹 출력")
    void sendPasswordResetEmail_emailMaskedInLog() {
        sender.sendPasswordResetEmail("alice@example.com", "some-token");

        String logOutput = capturedLogOutput();
        assertThat(logOutput).contains("a***@example.com");
        assertThat(logOutput).doesNotContain("alice@example.com");
    }

    @Test
    @DisplayName("sendPasswordResetEmail — null 이메일 → NPE 없이 [masked] 로 출력")
    void sendPasswordResetEmail_nullEmail_noNpeAndPrintsMasked() {
        assertThatNoException().isThrownBy(() ->
                sender.sendPasswordResetEmail(null, "some-token"));

        assertThat(capturedLogOutput()).contains("[masked]");
    }

    @Test
    @DisplayName("sendPasswordResetEmail — '@' 없는 이메일 → 예외 없이 [masked] 로 출력")
    void sendPasswordResetEmail_malformedEmail_printsMasked() {
        assertThatNoException().isThrownBy(() ->
                sender.sendPasswordResetEmail("not-an-email", "some-token"));

        assertThat(capturedLogOutput()).contains("[masked]");
    }

    @Test
    @DisplayName("sendPasswordResetEmail — local part 가 비어있는 이메일 → [masked] 로 출력")
    void sendPasswordResetEmail_emptyLocalPart_printsMasked() {
        assertThatNoException().isThrownBy(() ->
                sender.sendPasswordResetEmail("@example.com", "some-token"));

        assertThat(capturedLogOutput()).contains("[masked]");
    }

    private String capturedLogOutput() {
        return listAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", String::concat);
    }
}
