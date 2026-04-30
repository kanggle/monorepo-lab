package com.example.account.infrastructure.notifier;

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
 * Unit tests for {@link LoggingEmailVerificationNotifier} (TASK-BE-236).
 *
 * <p>Mirrors the {@code Slf4jEmailSenderUnitTest} pattern in {@code auth-service}
 * (TASK-BE-111) — Logback {@link ListAppender} captures emitted log events so
 * we can assert on:
 * <ul>
 *   <li>that the call completes without throwing,</li>
 *   <li>that an INFO log line is actually produced (the bug-class TASK-BE-236
 *       is fixing — silently missing notifier bean),</li>
 *   <li>that the verification token is never emitted (R4),</li>
 *   <li>that the recipient email is masked before logging (R4).</li>
 * </ul>
 */
@DisplayName("LoggingEmailVerificationNotifier 단위 테스트")
class LoggingEmailVerificationNotifierTest {

    private LoggingEmailVerificationNotifier notifier;
    private Logger notifierLogger;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        notifier = new LoggingEmailVerificationNotifier();
        notifierLogger = (Logger) LoggerFactory.getLogger(LoggingEmailVerificationNotifier.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        notifierLogger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        notifierLogger.detachAppender(listAppender);
    }

    @Test
    @DisplayName("sendVerificationEmail — 정상 이메일 → 예외 없이 완료")
    void sendVerificationEmail_validEmail_noException() {
        assertThatNoException().isThrownBy(() ->
                notifier.sendVerificationEmail("alice@example.com", "verify-token-xyz"));
    }

    @Test
    @DisplayName("sendVerificationEmail — INFO 로그 라인이 1개 이상 출력된다 (bean 미등록 회귀 방지)")
    void sendVerificationEmail_emitsAtLeastOneInfoLogLine() {
        notifier.sendVerificationEmail("alice@example.com", "some-token");

        assertThat(listAppender.list)
                .as("LoggingEmailVerificationNotifier must produce a log line per call — "
                        + "missing log output would mask the very bug TASK-BE-236 fixes")
                .isNotEmpty();
        assertThat(listAppender.list)
                .anySatisfy(event -> {
                    assertThat(event.getLevel().toString()).isEqualTo("INFO");
                    assertThat(event.getFormattedMessage()).contains("Email verification queued");
                });
    }

    @Test
    @DisplayName("sendVerificationEmail — verification 토큰이 로그에 출력되지 않음 (R4 보안 규칙)")
    void sendVerificationEmail_tokenNotLogged() {
        notifier.sendVerificationEmail("alice@example.com", "secret-verify-token-abc");

        String logOutput = capturedLogOutput();
        assertThat(logOutput).doesNotContain("secret-verify-token-abc");
    }

    @Test
    @DisplayName("sendVerificationEmail — 이메일 첫 글자 + *** + 도메인 형태로 마스킹 출력")
    void sendVerificationEmail_emailMaskedInLog() {
        notifier.sendVerificationEmail("alice@example.com", "some-token");

        String logOutput = capturedLogOutput();
        assertThat(logOutput).contains("a***@example.com");
        assertThat(logOutput).doesNotContain("alice@example.com");
    }

    @Test
    @DisplayName("sendVerificationEmail — null 이메일 → NPE 없이 [masked] 로 출력")
    void sendVerificationEmail_nullEmail_noNpeAndPrintsMasked() {
        assertThatNoException().isThrownBy(() ->
                notifier.sendVerificationEmail(null, "some-token"));

        assertThat(capturedLogOutput()).contains("[masked]");
    }

    @Test
    @DisplayName("sendVerificationEmail — '@' 없는 이메일 → 예외 없이 [masked] 로 출력")
    void sendVerificationEmail_malformedEmail_printsMasked() {
        assertThatNoException().isThrownBy(() ->
                notifier.sendVerificationEmail("not-an-email", "some-token"));

        assertThat(capturedLogOutput()).contains("[masked]");
    }

    @Test
    @DisplayName("sendVerificationEmail — local part 가 비어있는 이메일 → [masked] 로 출력")
    void sendVerificationEmail_emptyLocalPart_printsMasked() {
        assertThatNoException().isThrownBy(() ->
                notifier.sendVerificationEmail("@example.com", "some-token"));

        assertThat(capturedLogOutput()).contains("[masked]");
    }

    private String capturedLogOutput() {
        return listAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", String::concat);
    }
}
