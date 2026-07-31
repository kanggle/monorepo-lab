package com.example.fanplatform.community.infrastructure.membership;

import com.example.fanplatform.community.domain.membership.MembershipChecker;
import com.example.security.oauth2.client.IamClientCredentialsTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link MembershipCheckerAutoConfig} bean selection
 * (TASK-FAN-INT-002). Docker-free {@link ApplicationContextRunner} — runs in
 * {@code :community-service:check}, so the env-driven escape hatch the live-trio
 * e2e relies on is verified deterministically at PR time rather than only in the
 * nightly Testcontainers suite.
 *
 * <p>Contract:
 * <ul>
 *   <li>property absent (production default) → {@link HttpMembershipChecker} —
 *       net-zero, the {@code @ConditionalOnProperty(matchIfMissing=true)} keeps
 *       the real bean.</li>
 *   <li>{@code community.membership-service.enabled=true} → real bean.</li>
 *   <li>{@code community.membership-service.enabled=false} → the inert
 *       {@link AlwaysAllowMembershipChecker} fallback (the e2e live-trio path,
 *       where membership-service / iam are out of scope).</li>
 * </ul>
 *
 * <p>TASK-FAN-BE-041: {@code httpMembershipChecker} depends on the shared
 * {@code IamClientCredentialsTokenProvider} (ADR-MONO-058 § D6), which
 * {@link MembershipCheckerAutoConfig} itself now supplies via its own
 * {@code @Bean} factory method (constructor is network-free — it only
 * base64-encodes the client credentials and builds a {@code RestClient}) — no
 * manual {@code .withBean(...)} registration needed here any more.
 */
class MembershipCheckerAutoConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MembershipCheckerAutoConfig.class);

    @Test
    @DisplayName("property absent (prod default) -> HttpMembershipChecker (net-zero)")
    void defaultSelectsHttpChecker() {
        runner.run(ctx -> assertThat(ctx.getBean(MembershipChecker.class))
                .isInstanceOf(HttpMembershipChecker.class));
    }

    @Test
    @DisplayName("enabled=true -> HttpMembershipChecker")
    void enabledTrueSelectsHttpChecker() {
        runner.withPropertyValues("community.membership-service.enabled=true")
                .run(ctx -> assertThat(ctx.getBean(MembershipChecker.class))
                        .isInstanceOf(HttpMembershipChecker.class));
    }

    @Test
    @DisplayName("enabled=false -> AlwaysAllowMembershipChecker (e2e escape hatch)")
    void disabledSelectsStub() {
        runner.withPropertyValues("community.membership-service.enabled=false")
                .run(ctx -> assertThat(ctx.getBean(MembershipChecker.class))
                        .isInstanceOf(AlwaysAllowMembershipChecker.class));
    }

    /**
     * TASK-FAN-BE-041 (ADR-MONO-058 § D6): default config produces a token
     * provider bean at all — the shared class's constructor requires a
     * non-null, positive connect/read timeout (see next test), so a
     * successfully-constructed bean under production defaults is itself proof
     * the wired {@code 2000}/{@code 3000} ms defaults are non-zero.
     */
    @Test
    @DisplayName("default config -> IamClientCredentialsTokenProvider bean constructed (non-zero timeouts)")
    void defaultConfigConstructsTokenProviderBean() {
        runner.run(ctx -> assertThat(ctx.getBean(IamClientCredentialsTokenProvider.class)).isNotNull());
    }

    /**
     * Proves community-service's {@code iam.internal-client.connect-timeout-ms}
     * property actually flows into the shared class's constructor (not silently
     * dropped/hardcoded) — the shared class rejects a zero/negative timeout by
     * throwing {@link IllegalArgumentException} (ADR-MONO-058 § D6's own
     * "no-timeout" defect made structurally impossible to reintroduce). If this
     * wiring silently ignored the configured value, overriding it to 0 would
     * NOT fail context refresh.
     */
    @Test
    @DisplayName("connect-timeout-ms=0 -> context refresh fails (timeout config actually wired through)")
    void zeroConnectTimeoutFailsContextRefresh() {
        runner.withPropertyValues("iam.internal-client.connect-timeout-ms=0")
                .run(ctx -> {
                    assertThat(ctx.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class);
                    assertThat(rootCause(ctx.getStartupFailure())).hasMessageContaining("connectTimeout");
                });
    }

    private static Throwable rootCause(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}
