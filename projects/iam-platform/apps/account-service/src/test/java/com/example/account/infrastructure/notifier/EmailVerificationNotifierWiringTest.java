package com.example.account.infrastructure.notifier;

import com.example.account.application.port.EmailVerificationNotifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-MONO-770 — which notifier the context wires, from the REAL condition annotations of the two adapters and
 * Spring Boot's real mail auto-configuration (account-api.md § Delivery):
 *
 * <pre>
 *   iam.mail.enabled=true  (any profile, incl. prod / e2e) → SMTP
 *   enabled=false|absent, not prod                        → logging stub
 *   enabled=false|absent, prod                            → none → context fails (TASK-BE-236 fail-fast kept)
 *   enabled=true without spring.mail.host                 → context fails (no JavaMailSender)
 * </pre>
 *
 * <p>A consumer bean stands in for {@code SendVerificationEmailUseCase}: "no notifier" must mean «the context
 * does not start», not merely «the bean is absent».
 */
@DisplayName("EmailVerificationNotifier 배선 (TASK-MONO-770)")
class EmailVerificationNotifierWiringTest {

    @Configuration(proxyBeanMethods = false)
    static class NeedsANotifier {
        @Bean
        String consumer(EmailVerificationNotifier notifier) {
            return notifier.getClass().getSimpleName();
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
            .withUserConfiguration(LoggingEmailVerificationNotifier.class, SmtpEmailVerificationNotifier.class,
                    NeedsANotifier.class)
            .withPropertyValues(
                    "iam.mail.from=no-reply@iam.example.com",
                    "iam.mail.verification-link-base-url=https://auth.example.com/verify-email");

    @Test
    @DisplayName("AC-1: prod + iam.mail.enabled=true + spring.mail.host → SMTP 어댑터가 빈으로 뜬다")
    void prod_enabled_smtp() {
        runner.withPropertyValues("spring.profiles.active=prod", "iam.mail.enabled=true", "spring.mail.host=smtp.example.com")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(EmailVerificationNotifier.class);
                    assertThat(ctx).hasSingleBean(SmtpEmailVerificationNotifier.class);
                    assertThat(ctx).doesNotHaveBean(LoggingEmailVerificationNotifier.class);
                });
    }

    @Test
    @DisplayName("🔴 데모 모양: e2e 프로필 + iam.mail.enabled=true → SMTP (프로필이 아니라 설정이 고른다)")
    void e2eProfile_enabled_smtp() {
        runner.withPropertyValues("spring.profiles.active=e2e", "iam.mail.enabled=true", "spring.mail.host=iam-mailpit")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(SmtpEmailVerificationNotifier.class);
                    assertThat(ctx).doesNotHaveBean(LoggingEmailVerificationNotifier.class);
                });
    }

    @Test
    @DisplayName("꺼짐 + prod 아님 → 로깅 스텁 (켜짐 키가 없어도 같다)")
    void disabled_nonProd_loggingStub() {
        runner.withPropertyValues("spring.profiles.active=e2e", "iam.mail.enabled=false")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(LoggingEmailVerificationNotifier.class);
                    assertThat(ctx).doesNotHaveBean(SmtpEmailVerificationNotifier.class);
                });
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasSingleBean(LoggingEmailVerificationNotifier.class);
        });
    }

    @Test
    @DisplayName("🔴 fail-fast 유지: prod + 꺼짐(또는 미설정) → notifier 없음 → 컨텍스트 기동 실패")
    void prod_disabled_failsFast() {
        runner.withPropertyValues("spring.profiles.active=prod", "iam.mail.enabled=false")
                .run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("spring.profiles.active=prod")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    @DisplayName("켜짐인데 spring.mail.host 없음 → JavaMailSender 없음 → 기동 실패 (조용한 매 발송 실패가 아니라)")
    void enabled_withoutHost_failsFast() {
        runner.withPropertyValues("spring.profiles.active=e2e", "iam.mail.enabled=true")
                .run(ctx -> assertThat(ctx).hasFailed());
    }
}
