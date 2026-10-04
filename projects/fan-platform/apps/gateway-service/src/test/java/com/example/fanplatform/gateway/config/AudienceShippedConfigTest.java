package com.example.fanplatform.gateway.config;

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
 * (TASK-MONO-696 AC-4/AC-5, TASK-MONO-697 AC-2).
 *
 * <p><strong>The ENFORCE pin is a rollout guard, not a style check.</strong> Phase 2 (reject) was
 * switched on for all six gateways in one reviewed change (TASK-MONO-697), after the measured
 * mismatch count was zero. Flipping the default back in {@code application.yml}, or pinning the
 * mode in a deployment file, turns this suite red.
 *
 * <p><strong>The rollback lever is pinned too.</strong> {@code docker-compose.yml} — the file the gateway
 * container is started from — must hand {@code OIDC_AUDIENCE_MODE} to it as a pass-through that
 * defaults to ENFORCE, so the mode can be reverted on a running host without a rebuild. Asserting
 * only "no override" could not tell a wired lever from a missing one; the second cell below is
 * what fails when the line is gone.
 */
@DisplayName("fan gateway — 출하되는 audience 설정 (TASK-MONO-696)")
class AudienceShippedConfigTest {

    private static final String PREFIX = "fanplatform.oauth2.";

    @Nested
    @DisplayName("출하 값")
    class Shipped {

        @Test
        @DisplayName("audience-mode 는 ENFORCE 로 출하된다 (phase 2 — TASK-MONO-697)")
        void shipsEnforce() {
            assertThat(ShippedAudienceConfig.shippedValue(PREFIX + "audience-mode")).isEqualTo("ENFORCE");
        }

        @Test
        @DisplayName("allowed-audiences 는 실측된 도달 client 그대로다 (AC-1 (b))")
        void shipsMeasuredAllowlist() {
            assertThat(GatewayJwtDecoders.parseCsv(ShippedAudienceConfig.shippedValue(PREFIX + "allowed-audiences")))
                    // TASK-MONO-751: the console's fan-directory screens reach this edge with the
                    // assume-tenant token, whose aud is the console client.
                    .containsExactly("fan-platform-user-flow-client", "platform-console-web");
        }

        @Test
        @DisplayName("프로젝트 compose · .env 는 audience mode 를 전달 줄 형태로만 다룬다 — 다른 값 고정 · .env 의 SHADOW 고정 없음")
        void deploymentFilesOnlyPassTheModeThrough() {
            assertThat(ShippedAudienceConfig.modeOverrides(Path.of("../..")))
                    .isEmpty();
        }

        @Test
        @DisplayName("docker-compose.yml 의 gateway-service 가 OIDC_AUDIENCE_MODE 를 ${OIDC_AUDIENCE_MODE:-ENFORCE} 로 넘긴다 (되돌리기 레버)")
        void runningComposePassesTheModeThroughToTheGateway() {
            assertThat(ShippedAudienceConfig.servicesPassingModeThrough(Path.of("../../docker-compose.yml")))
                    .containsExactly("gateway-service");
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
                        PREFIX + "allowed-issuers=http://iam.local",
                        // The JWKS startup probe would dial the (unreachable) URI; it is not under test.
                        "gateway.jwks.startup-probe.enabled=false");

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
