package com.example.fanplatform.community.infrastructure.membership;

import com.example.fanplatform.community.domain.membership.MembershipChecker;
import com.example.security.oauth2.client.IamClientCredentialsTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link MembershipCheckerAutoConfig} bean selection. Docker-free
 * {@link ApplicationContextRunner}, so it runs in {@code :community-service:check}
 * at PR time rather than only in the nightly Testcontainers suite.
 *
 * <p><b>TASK-FAN-INT-006 inverted this test rather than deleting cases.</b> It used
 * to assert that {@code community.membership-service.enabled=false} selected the
 * inert {@code AlwaysAllowMembershipChecker}. Deleting those cases would have taken
 * the whole axis out of the audit — nothing would then state, in an executable
 * place, that the property no longer does anything. So the "off" cases now assert
 * the opposite: the property is inert and the real checker survives it.
 *
 * <p>Contract now:
 * <ul>
 *   <li>property absent (production default) → {@link HttpMembershipChecker}.</li>
 *   <li>{@code community.membership-service.enabled=true} → {@link HttpMembershipChecker}.</li>
 *   <li>{@code community.membership-service.enabled=false} → <b>still</b>
 *       {@link HttpMembershipChecker}. The key is dead; setting it cannot open
 *       the gate.</li>
 *   <li>an arbitrary unrelated value → same.</li>
 *   <li><b>structurally</b>: this configuration declares exactly one
 *       {@code MembershipChecker} {@code @Bean} method.</li>
 * </ul>
 *
 * <p><b>Why the structural case is the load-bearing one.</b> Every property case
 * above keys on one literal property name. A hatch reintroduced behind a
 * <em>different</em> key, or as a plain unconditional second bean, passes all of
 * them. TASK-FAN-INT-005 measured exactly that on the artist side: with a hatch
 * injected, all seven property-based cases stayed green and only the structural
 * assertion went red.
 */
class MembershipCheckerAutoConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MembershipCheckerAutoConfig.class);

    @Test
    @DisplayName("property absent (prod default) -> HttpMembershipChecker")
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
    @DisplayName("enabled=false -> STILL HttpMembershipChecker (the hatch is gone, TASK-FAN-INT-006)")
    void disabledNoLongerOpensTheGate() {
        runner.withPropertyValues("community.membership-service.enabled=false")
                .run(ctx -> assertThat(ctx.getBean(MembershipChecker.class))
                        .as("the escape hatch was deleted; this property is inert and "
                                + "must not be able to select a permissive checker")
                        .isInstanceOf(HttpMembershipChecker.class));
    }

    @Test
    @DisplayName("an arbitrary value on the dead key -> STILL HttpMembershipChecker")
    void arbitraryValueOnTheDeadKeyChangesNothing() {
        // `havingValue` comparison used to be case-insensitive, so "FALSE" and "false"
        // were the same door. Both are asserted shut, and so is a value that was never
        // meaningful — a re-added @ConditionalOnProperty would have to match something.
        runner.withPropertyValues("community.membership-service.enabled=FALSE")
                .run(ctx -> assertThat(ctx.getBean(MembershipChecker.class))
                        .isInstanceOf(HttpMembershipChecker.class));
        runner.withPropertyValues("community.membership-service.enabled=disabled")
                .run(ctx -> assertThat(ctx.getBean(MembershipChecker.class))
                        .isInstanceOf(HttpMembershipChecker.class));
    }

    /**
     * The structural guard. Reflection over the configuration class itself, so a
     * second {@code MembershipChecker} bean is caught regardless of what condition
     * (or none) is attached to it and regardless of what it is named.
     */
    @Test
    @DisplayName("structurally: exactly one MembershipChecker @Bean method is declared")
    void declaresExactlyOneMembershipCheckerBean() {
        List<String> beanMethods = Arrays.stream(
                        MembershipCheckerAutoConfig.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Bean.class))
                .filter(m -> MembershipChecker.class.isAssignableFrom(m.getReturnType()))
                .map(Method::getName)
                .sorted()
                .toList();

        assertThat(beanMethods)
                .as("a second MembershipChecker @Bean is how the escape hatch comes back — "
                        + "under any property key, or under none at all")
                .containsExactly("httpMembershipChecker");
    }

    /**
     * TASK-FAN-BE-041 (ADR-MONO-058 § D6): default config produces a token
     * provider bean at all — the shared class's constructor requires a non-null,
     * positive connect/read timeout (see next test), so a successfully-constructed
     * bean under production defaults is itself proof the wired {@code 2000}/{@code
     * 3000} ms defaults are non-zero.
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
