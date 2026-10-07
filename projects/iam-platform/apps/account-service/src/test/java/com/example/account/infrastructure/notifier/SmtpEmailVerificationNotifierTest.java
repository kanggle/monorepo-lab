package com.example.account.infrastructure.notifier;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.account.application.exception.EmailDeliveryException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.InternetAddress;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * TASK-MONO-770 — the SMTP adapter against a mocked {@link JavaMailSender} (no GreenMail in this repo; the
 * transport itself is Spring's). What is asserted is what this class adds: the mail carries the link with the
 * token, the log line carries neither the token nor the full address, and a failure is classified without
 * leaking the address through the exception.
 */
@DisplayName("SmtpEmailVerificationNotifier (TASK-MONO-770)")
class SmtpEmailVerificationNotifierTest {

    private static final String TO = "alice@example.com";
    private static final String TOKEN = "3f1c2e8a-1111-4222-8333-944455556666";
    private static final String LINK_BASE = "https://auth.example.com/verify-email";

    private JavaMailSender mailSender;
    private SmtpEmailVerificationNotifier notifier;
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        notifier = new SmtpEmailVerificationNotifier(mailSender, "no-reply@iam.example.com", LINK_BASE);
        logger = (Logger) LoggerFactory.getLogger(SmtpEmailVerificationNotifier.class);
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
    @DisplayName("보내는 메일: 받는 사람 · 보낸 사람 · 제목 · 본문의 링크 = <base>?token=<token>")
    void sendsMailWithTokenLink() {
        notifier.sendVerificationEmail(TO, TOKEN);

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());
        SimpleMailMessage mail = sent.getValue();
        assertThat(mail.getTo()).containsExactly(TO);
        assertThat(mail.getFrom()).isEqualTo("no-reply@iam.example.com");
        assertThat(mail.getSubject()).isEqualTo(SmtpEmailVerificationNotifier.SUBJECT);
        assertThat(mail.getText()).contains(LINK_BASE + "?token=" + TOKEN);
    }

    @Test
    @DisplayName("🔴 R4: 성공 로그에 토큰도 전체 주소도 없다 — 마스킹 주소만")
    void successLog_neitherTokenNorFullAddress() {
        notifier.sendVerificationEmail(TO, TOKEN);

        assertThat(logs()).contains("a***@example.com");
        assertThat(logs()).doesNotContain(TOKEN).doesNotContain(TO);
    }

    @Test
    @DisplayName("연결 실패 → TRANSIENT · 로그·예외 메시지 어디에도 주소·토큰 없음 (SMTP 문구가 주소를 담아도)")
    void connectionFailure_transient_noLeak() {
        doThrow(new MailSendException("Mail server connection failed for " + TO,
                new jakarta.mail.MessagingException("Could not connect to SMTP host; rcpt " + TO)))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> notifier.sendVerificationEmail(TO, TOKEN))
                .isInstanceOf(EmailDeliveryException.class)
                .satisfies(e -> {
                    EmailDeliveryException ex = (EmailDeliveryException) e;
                    assertThat(ex.getKind()).isEqualTo(EmailDeliveryException.Kind.TRANSIENT);
                    assertThat(ex.getMessage()).doesNotContain(TO).doesNotContain(TOKEN);
                    assertThat(ex.getCause()).as("no cause: it would carry the SMTP text (and the address)").isNull();
                });
        assertThat(logs()).doesNotContain(TO).doesNotContain(TOKEN).contains("a***@example.com");
    }

    @Test
    @DisplayName("수신자 거부(5xx — invalid addresses) → PERMANENT")
    void recipientRejected_permanent() throws Exception {
        SendFailedException rejected = new SendFailedException("550 5.1.1 user unknown", null,
                new InternetAddress[0], new InternetAddress[0], new InternetAddress[]{new InternetAddress(TO)});
        doThrow(new MailSendException(Map.of(new Object(), rejected)))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> notifier.sendVerificationEmail(TO, TOKEN))
                .isInstanceOf(EmailDeliveryException.class)
                .extracting(e -> ((EmailDeliveryException) e).getKind())
                .isEqualTo(EmailDeliveryException.Kind.PERMANENT);
    }

    @Test
    @DisplayName("토큰은 링크에 인코딩되어 실린다 (쿼리 한 개)")
    void linkCarriesTokenAsSingleQueryParam() {
        assertThat(notifier.link(TOKEN)).isEqualTo(LINK_BASE + "?token=" + TOKEN);
    }
}
