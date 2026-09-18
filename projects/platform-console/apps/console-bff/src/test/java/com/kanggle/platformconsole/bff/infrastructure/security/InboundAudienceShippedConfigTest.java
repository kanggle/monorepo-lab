package com.kanggle.platformconsole.bff.infrastructure.security;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What console-bff ships for the rule-5 audience check, and what happens when it ships nothing —
 * {@code TASK-MONO-712} AC-1.
 *
 * <p><strong>This suite is the reason the check is a bean and not a property.</strong> AC-1 does
 * not ask for "an audience check exists"; it asks for one that <em>cannot be switched off by
 * omission</em>. Spring Boot's {@code spring.security.oauth2.resourceserver.jwt.audiences} fails
 * that: with the list absent or empty it registers no validator at all, so blanking the value
 * removes the check and <em>nothing turns red anywhere</em>. The cells below are the executable
 * difference — blank the value here and the context does not start.
 *
 * <p>🔴 {@link StartupFailure} exists because {@link Shipped} alone would be a weaker claim than it
 * looks. Asserting the shipped string proves what today's file says, not that tomorrow's empty
 * file is refused; those are different properties and only the second is what the contract
 * requires.
 */
@DisplayName("console-bff — 출하되는 audience 설정 (TASK-MONO-712 AC-1)")
class InboundAudienceShippedConfigTest {

    /** The one client id this edge admits — measured in TASK-MONO-712 § AC-0, not assumed. */
    private static final String MEASURED_CLIENT = "platform-console-web";

    @Nested
    @DisplayName("출하 값")
    class Shipped {

        @Test
        @DisplayName("application.yml 의 기본값이 실측된 client 그대로다")
        void shipsMeasuredAllowlist() throws IOException {
            assertThat(shippedLine())
                    .as("the shipped default must be the measured production client")
                    .isEqualTo("allowed-audiences: ${CONSOLE_BFF_ALLOWED_AUDIENCES:"
                            + MEASURED_CLIENT + "}");
        }

        /**
         * 🔴 The failure this cell exists for is not "a wrong value" — it is the specific wrong
         * value that makes the other seven suites green for the wrong reason. Before AC-4 the
         * integration fixtures minted {@code aud = "console-bff"}; admitting that here would have
         * turned them green while shipping an edge that accepts a token no client can obtain
         * (TASK-MONO-696 AC-5 — a service name is not a credential value).
         */
        @Test
        @DisplayName("서비스 이름 'console-bff' 는 allowlist 에 없다 — 운영 토큰은 그 값을 못 가진다")
        void doesNotAdmitItsOwnServiceName() throws IOException {
            assertThat(shippedLine()).doesNotContain("console-bff");
        }

        private String shippedLine() throws IOException {
            try (InputStream in = getClass().getResourceAsStream("/application.yml")) {
                Assertions.assertThat(in).as("console-bff application.yml on the test classpath")
                        .isNotNull();
                String yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                return yaml.lines()
                        .map(String::trim)
                        .filter(line -> line.startsWith("allowed-audiences:"))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError(
                                "application.yml declares no `allowed-audiences` key — rule 5 is "
                                        + "not configured at this edge at all"));
            }
        }
    }

    @Nested
    @DisplayName("기동 실패 — allowlist 는 비울 수 없다 (속성만으로는 못 하는 바로 그것)")
    class StartupFailure {

        // PropertyPlaceholderAutoConfiguration is what a booted service has and a bare runner does
        // not: without it an unresolvable ${...} is injected as its own literal text rather than
        // failing, and the "key absent" cell would pass as a one-element allowlist holding the
        // literal "${console-bff.security.allowed-audiences}". The gateways' AudienceShippedConfigTest
        // measured that trap first; this suite inherits the fix rather than re-discovering it.
        private final ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
                .withUserConfiguration(InboundAudienceConfig.class)
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .withPropertyValues(
                        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://127.0.0.1:1/jwks",
                        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://iam.local");

        @Test
        @DisplayName("대조군: allowlist 가 있으면 디코더 빈이 만들어진다")
        void control_validConfig_starts() {
            runner.withPropertyValues("console-bff.security.allowed-audiences=" + MEASURED_CLIENT)
                    .run(ctx -> assertThat(ctx).hasNotFailed().hasSingleBean(JwtDecoder.class));
        }

        @Test
        @DisplayName("bite — allowlist 를 비우면 기동이 실패한다 (검사가 조용히 사라지지 않는다)")
        void emptyAllowlist_failsStartup() {
            runner.withPropertyValues("console-bff.security.allowed-audiences=")
                    .run(ctx -> {
                        assertThat(ctx).hasFailed();
                        Assertions.assertThat(ctx.getStartupFailure())
                                .rootCause().isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("allowedAudiences");
                    });
        }

        @Test
        @DisplayName("bite — 속성 키를 통째로 지워도 기동이 실패한다 (부재 = 공백)")
        void missingAllowlist_failsStartup() {
            runner.run(ctx -> {
                assertThat(ctx).hasFailed();
                Assertions.assertThat(ctx.getStartupFailure())
                        .rootCause().isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("allowedAudiences");
            });
        }

        /**
         * 🔴 A whitespace-only value is the shape a bad deployment override actually takes
         * ({@code CONSOLE_BFF_ALLOWED_AUDIENCES=" "} from a templated env file). It must land on the
         * failing side, not be trimmed into a one-element allowlist of {@code ""}.
         */
        @Test
        @DisplayName("bite — 공백만 있는 값도 기동 실패 (덮어쓰기 사고의 실제 모양)")
        void blankAllowlist_failsStartup() {
            runner.withPropertyValues("console-bff.security.allowed-audiences=   ")
                    .run(ctx -> {
                        assertThat(ctx).hasFailed();
                        Assertions.assertThat(ctx.getStartupFailure())
                                .rootCause().isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("allowedAudiences");
                    });
        }
    }
}
