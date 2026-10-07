package com.example.auth.infrastructure.email;

import com.example.auth.application.port.EmailSenderPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-MONO-770 — which {@link EmailSenderPort} the context wires, from the real conditions of the two adapters
 * and Spring Boot's real mail auto-configuration. Same table as account-service's notifier: property selects
 * SMTP in any profile; mail off outside prod → logging stub; mail off in prod → the context fails (TASK-BE-242).
 */
@DisplayName("EmailSenderPort 배선 (TASK-MONO-770)")
class EmailSenderWiringTest {

    @Configuration(proxyBeanMethods = false)
    static class NeedsASender {
        @Bean
        String consumer(EmailSenderPort sender) {
            return sender.getClass().getSimpleName();
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
            .withUserConfiguration(LoggingEmailSender.class, SmtpEmailSender.class, NeedsASender.class)
            .withPropertyValues(
                    "iam.mail.from=no-reply@iam.example.com",
                    "iam.mail.password-reset-link-base-url=https://auth.example.com/password-reset");

    @Test
    @DisplayName("prod · e2e 어느 쪽이든 iam.mail.enabled=true + spring.mail.host → SMTP")
    void enabled_smtp_anyProfile() {
        for (String profile : new String[]{"prod", "e2e"}) {
            runner.withPropertyValues("spring.profiles.active=" + profile, "iam.mail.enabled=true",
                            "spring.mail.host=smtp.example.com")
                    .run(ctx -> {
                        assertThat(ctx).hasNotFailed();
                        assertThat(ctx).hasSingleBean(SmtpEmailSender.class);
                        assertThat(ctx).doesNotHaveBean(LoggingEmailSender.class);
                    });
        }
    }

    @Test
    @DisplayName("꺼짐 + prod 아님 → 로깅 스텁")
    void disabled_nonProd_logging() {
        runner.withPropertyValues("spring.profiles.active=e2e")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(LoggingEmailSender.class);
                    assertThat(ctx).doesNotHaveBean(SmtpEmailSender.class);
                });
    }

    @Test
    @DisplayName("🔴 fail-fast 유지: prod + 꺼짐 → sender 없음 → 기동 실패 · 켜짐인데 host 없음 → 기동 실패")
    void failFast() {
        runner.withPropertyValues("spring.profiles.active=prod", "iam.mail.enabled=false")
                .run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("spring.profiles.active=e2e", "iam.mail.enabled=true")
                .run(ctx -> assertThat(ctx).hasFailed());
    }
}
