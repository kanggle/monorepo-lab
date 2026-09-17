package com.wms.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.apigateway.security.GatewayJwtDecoders;
import com.example.apigateway.testfixtures.ShippedAudienceConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

/**
 * What this gateway ships for the audience check, and what happens when it ships nothing
 * (TASK-MONO-696 AC-4/AC-5).
 *
 * <p><strong>The SHADOW pin is a rollout guard, not a style check.</strong> Rejection is phase 2,
 * a separate reviewed change taken only after the measured mismatch count is zero. Flipping the
 * default in {@code application.yml} (or overriding it in a deployment file) without that change
 * turns this suite red. The phase-2 change edits the expectation here on purpose.
 */
@DisplayName("wms gateway — 출하되는 audience 설정 (TASK-MONO-696)")
class AudienceShippedConfigTest {

    private static final String PREFIX = "wms.oauth2.";

    @Nested
    @DisplayName("출하 값")
    class Shipped {

        @Test
        @DisplayName("audience-mode 는 SHADOW 로 출하된다 — ENFORCE 는 별도 변경(phase 2)")
        void shipsShadow() {
            assertThat(ShippedAudienceConfig.shippedValue(PREFIX + "audience-mode")).isEqualTo("SHADOW");
        }

        @Test
        @DisplayName("allowed-audiences 는 실측된 도달 client 그대로다 (AC-1 (b))")
        void shipsMeasuredAllowlist() {
            assertThat(GatewayJwtDecoders.parseCsv(ShippedAudienceConfig.shippedValue(PREFIX + "allowed-audiences")))
                    .containsExactly("platform-console-web");
            assertThat(ShippedAudienceConfig.shippedValue(PREFIX + "allowed-audiences"))
                    .isEqualTo(SecurityConfigRealDecoderPathTest.SHIPPED_ALLOWED_AUDIENCES);
        }

        @Test
        @DisplayName("프로젝트 compose · .env 어디에도 audience mode 를 ENFORCE 로 덮는 줄이 없다")
        void noDeploymentFileOverridesToEnforce() {
            assertThat(ShippedAudienceConfig.enforceOverrides(Path.of("../..")))
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("기동 실패 — allowlist·mode 는 비울 수 없다")
    class StartupFailure {

        // PropertyPlaceholderAutoConfiguration is what a booted gateway has and a bare runner does
        // not: without it an unresolvable ${...} is injected as its own literal text instead of
        // failing — measured while writing this suite (the "key absent" cell went green-for-the-
        // wrong-reason as a non-empty one-element allowlist).
        private final ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration.class))
                .withUserConfiguration(OAuth2ResourceServerConfig.class)
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .withPropertyValues(
                        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://127.0.0.1:1/jwks",
                        PREFIX + "allowed-issuers=http://iam.local");

        @Test
        @DisplayName("대조군: allowlist + SHADOW 가 있으면 디코더가 만들어진다")
        void control_validConfig_starts() {
            runner.withPropertyValues(PREFIX + "allowed-audiences=platform-console-web", PREFIX + "audience-mode=SHADOW")
                    .run(ctx -> assertThat(ctx).hasNotFailed().hasSingleBean(ReactiveJwtDecoder.class));
        }

        @Test
        @DisplayName("빈 allowlist → 기동 실패 (IllegalArgumentException)")
        void emptyAllowlist_failsStartup() {
            runner.withPropertyValues(PREFIX + "allowed-audiences=", PREFIX + "audience-mode=SHADOW")
                    .run(ctx -> {
                        assertThat(ctx).hasFailed();
                        Assertions.assertThat(ctx.getStartupFailure())
                                .rootCause().isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("allowedAudiences");
                    });
        }

        @Test
        @DisplayName("allowlist 키 부재 → 기동 실패 (placeholder 해석 불가)")
        void missingAllowlist_failsStartup() {
            runner.withPropertyValues(PREFIX + "audience-mode=SHADOW")
                    .run(ctx -> {
                        assertThat(ctx).hasFailed();
                        Assertions.assertThat(ctx.getStartupFailure())
                                .rootCause().hasMessageContaining(PREFIX + "allowed-audiences");
                    });
        }

        @Test
        @DisplayName("mode 부재 · 모르는 mode → 기동 실패 (기본값 없음)")
        void missingOrUnknownMode_failsStartup() {
            runner.withPropertyValues(PREFIX + "allowed-audiences=platform-console-web")
                    .run(ctx -> {
                        assertThat(ctx).hasFailed();
                        Assertions.assertThat(ctx.getStartupFailure())
                                .rootCause().hasMessageContaining(PREFIX + "audience-mode");
                    });
            runner.withPropertyValues(PREFIX + "allowed-audiences=platform-console-web", PREFIX + "audience-mode=OFF")
                    .run(ctx -> {
                        assertThat(ctx).hasFailed();
                        Assertions.assertThat(ctx.getStartupFailure())
                                .rootCause().isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("OFF");
                    });
        }
    }
}
