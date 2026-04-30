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

@DisplayName("Slf4jEmailSender 단위 테스트")
class Slf4jEmailSenderUnitTest {

    private Slf4jEmailSender sender;
    private Logger senderLogger;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        sender = new Slf4jEmailSender();
        senderLogger = (Logger) LoggerFactory.getLogger(Slf4jEmailSender.class);
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
    @DisplayName("sendPasswordResetEmail — null 이메일 → 예외 없이 완료 ([masked] 출력)")
    void sendPasswordResetEmail_nullEmail_noException() {
        assertThatNoException().isThrownBy(() ->
                sender.sendPasswordResetEmail(null, "some-token"));

        assertThat(capturedLogOutput()).contains("[masked]");
    }

    private String capturedLogOutput() {
        return listAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", String::concat);
    }
}
