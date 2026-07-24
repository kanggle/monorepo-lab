package com.example.fanplatform.community.infrastructure.membership;

import com.example.fanplatform.community.domain.membership.MembershipChecker;
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
 */
class MembershipCheckerAutoConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            // httpMembershipChecker depends on the token provider; register a real
            // instance (constructor is network-free — it only base64-encodes the
            // client credentials and builds a RestClient).
            .withBean(IamClientCredentialsTokenProvider.class,
                    () -> new IamClientCredentialsTokenProvider(
                            "http://iam.local/oauth2/token",
                            "community-service-client",
                            "secret",
                            "membership.read"))
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
}
