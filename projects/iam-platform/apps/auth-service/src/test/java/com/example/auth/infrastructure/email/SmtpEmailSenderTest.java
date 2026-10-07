package com.example.auth.infrastructure.email;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.auth.application.exception.EmailSendException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * TASK-MONO-770 (owner decision 2026-10-07) — the password-reset SMTP sender against a mocked
 * {@link JavaMailSender}: the mail carries the reset link with the token; the logs carry neither the token nor
 * the full address; a failure is wrapped in {@link EmailSendException} (the port contract) without the SMTP text.
 */
@DisplayName("SmtpEmailSender (TASK-MONO-770)")
class SmtpEmailSenderTest {

    private static final String TO = "bob@example.com";
    private static final String TOKEN = "9a8b7c6d-0000-4111-8222-333344445555";
    private static final String LINK_BASE = "https://auth.example.com/password-reset";

    private JavaMailSender mailSender;
    private SmtpEmailSender sender;
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        sender = new SmtpEmailSender(mailSender, "no-reply@iam.example.com", LINK_BASE);
        logger = (Logger) LoggerFactory.getLogger(SmtpEmailSender.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    private String logs() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + "\n" + b);
    }

    @Test
    @DisplayName("메일 = 받는 사람 · 제목 · 본문 링크 <base>?token=<token> · 로그엔 마스킹 주소만(토큰·전체 주소 없음)")
    void sendsResetLink_logsWithoutSecrets() {
        sender.sendPasswordResetEmail(TO, TOKEN);

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());
        assertThat(sent.getValue().getTo()).containsExactly(TO);
        assertThat(sent.getValue().getSubject()).isEqualTo(SmtpEmailSender.SUBJECT);
        assertThat(sent.getValue().getText()).contains(LINK_BASE + "?token=" + TOKEN);
        assertThat(logs()).contains("b***@example.com").doesNotContain(TOKEN).doesNotContain(TO);
    }

    @Test
    @DisplayName("발송 실패 → EmailSendException(원인 없음 · 주소·토큰 없는 메시지) · 로그에도 없음")
    void failure_wrapped_noLeak() {
        doThrow(new MailSendException("550 rejected " + TO))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> sender.sendPasswordResetEmail(TO, TOKEN))
                .isInstanceOf(EmailSendException.class)
                .satisfies(e -> {
                    assertThat(e.getMessage()).doesNotContain(TO).doesNotContain(TOKEN);
                    assertThat(e.getCause()).isNull();
                });
        assertThat(logs()).doesNotContain(TO).doesNotContain(TOKEN);
    }
}
