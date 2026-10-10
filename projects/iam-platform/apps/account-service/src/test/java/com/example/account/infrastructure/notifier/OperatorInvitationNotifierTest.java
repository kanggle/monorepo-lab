package com.example.account.infrastructure.notifier;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.account.application.exception.EmailDeliveryException;
import com.example.account.application.port.OperatorInvitationNotifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * TASK-MONO-772 S2 — the invitation mail adapters: the link carries the token and nothing else does (R4), the
 * failure is classified like the 770 mail, and the wiring follows the same property as the verification mail.
 */
@DisplayName("OperatorInvitationNotifier — SMTP 본문 · 실패 분류 · 배선 (TASK-MONO-772 S2)")
class OperatorInvitationNotifierTest {

    private static final String TOKEN = "Zm9vYmFyLXRva2VuLTMyLWJ5dGVzLWV4YW1wbGU";
    private static final Instant EXP = Instant.parse("2026-10-17T10:00:00Z");

    /**
     * Captures this package's log events directly (a logback {@link ListAppender} on the logger), not stdout:
     * under the {@code test} profile {@code logback-spring.xml} attaches no root appender, so an stdout capture
     * would be empty — and «the token is not in an empty string» proves nothing.
     */
    private static ListAppender<ILoggingEvent> capture(Class<?> type) {
        Logger logger = (Logger) LoggerFactory.getLogger(type);
        logger.setLevel(Level.INFO);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private static String logged(ListAppender<ILoggingEvent> appender) {
        StringBuilder sb = new StringBuilder();
        appender.list.forEach(e -> sb.append(e.getFormattedMessage()).append('\n'));
        return sb.toString();
    }

    @Test
    @DisplayName("🔴 R4: 본문 링크 = base?token=… · 회사 · 초대자 · 만료 · 로그에는 마스킹된 주소만, 토큰 없음")
    void smtp_bodyAndLogs() {
        ListAppender<ILoggingEvent> log = capture(SmtpOperatorInvitationNotifier.class);
        JavaMailSender sender = mock(JavaMailSender.class);
        SmtpOperatorInvitationNotifier notifier = new SmtpOperatorInvitationNotifier(
                sender, "no-reply@iam.example.com", "https://iam.example.com/operator-invitations/accept");

        notifier.sendOperatorInvitation("person@example.com", TOKEN, "에이크미", "김관리", EXP);

        ArgumentCaptor<SimpleMailMessage> msg = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(msg.capture());
        String text = msg.getValue().getText();
        assertThat(text).contains("https://iam.example.com/operator-invitations/accept?token=" + TOKEN)
                .contains("에이크미").contains("김관리 님이").contains("2026-10-17 10:00 (UTC)");
        assertThat(msg.getValue().getTo()).containsExactly("person@example.com");
        assertThat(msg.getValue().getSubject()).isEqualTo(SmtpOperatorInvitationNotifier.SUBJECT);
        assertThat(logged(log)).contains("p***@example.com").doesNotContain(TOKEN).doesNotContain("person@example.com");
    }

    @Test
    @DisplayName("초대자 없음 → 그 문장을 뺀다")
    void body_withoutInviter() {
        assertThat(SmtpOperatorInvitationNotifier.body("L", "acme-corp", null, EXP))
                .doesNotContain("님이").contains("«acme-corp» 의 운영자로");
    }

    @Test
    @DisplayName("SMTP 실패 → EmailDeliveryException(고정 메시지, 원인 없음) · 로그·메시지에 토큰 없음")
    void smtp_failure() {
        ListAppender<ILoggingEvent> log = capture(SmtpOperatorInvitationNotifier.class);
        JavaMailSender sender = mock(JavaMailSender.class);
        doThrow(new MailSendException("connection refused to smtp")).when(sender).send(any(SimpleMailMessage.class));
        SmtpOperatorInvitationNotifier notifier = new SmtpOperatorInvitationNotifier(
                sender, "no-reply@iam.example.com", "https://iam.example.com/operator-invitations/accept");

        assertThatThrownBy(() -> notifier.sendOperatorInvitation("person@example.com", TOKEN, "acme", null, EXP))
                .isInstanceOfSatisfying(EmailDeliveryException.class, e -> {
                    assertThat(e.getCause()).isNull();
                    assertThat(e.getMessage()).doesNotContain(TOKEN).doesNotContain("person@");
                });
        assertThat(logged(log)).contains("p***@example.com").doesNotContain(TOKEN);
    }

    @Test
    @DisplayName("로깅 스텁: 토큰은 버리고 마스킹된 주소만")
    void loggingStub() {
        ListAppender<ILoggingEvent> log = capture(LoggingOperatorInvitationNotifier.class);
        new LoggingOperatorInvitationNotifier().sendOperatorInvitation("person@example.com", TOKEN, "acme", "x", EXP);
        assertThat(logged(log)).contains("p***@example.com").doesNotContain(TOKEN);
    }

    @Configuration(proxyBeanMethods = false)
    static class NeedsANotifier {
        @Bean
        String consumer(OperatorInvitationNotifier notifier) {
            return notifier.getClass().getSimpleName();
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
            .withUserConfiguration(LoggingOperatorInvitationNotifier.class, SmtpOperatorInvitationNotifier.class,
                    NeedsANotifier.class)
            .withPropertyValues(
                    "iam.mail.from=no-reply@iam.example.com",
                    "iam.mail.operator-invitation-link-base-url=https://iam.example.com/operator-invitations/accept");

    @Test
    @DisplayName("배선: 켜짐 → SMTP · 꺼짐 + prod 아님 → 스텁 · 꺼짐 + prod → 기동 실패 (770 과 같은 규칙)")
    void wiring() {
        runner.withPropertyValues("spring.profiles.active=e2e", "iam.mail.enabled=true", "spring.mail.host=iam-mailpit")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(SmtpOperatorInvitationNotifier.class);
                    assertThat(ctx).doesNotHaveBean(LoggingOperatorInvitationNotifier.class);
                });
        runner.withPropertyValues("spring.profiles.active=e2e").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasSingleBean(LoggingOperatorInvitationNotifier.class);
        });
        runner.withPropertyValues("spring.profiles.active=prod").run(ctx -> assertThat(ctx).hasFailed());
    }
}
