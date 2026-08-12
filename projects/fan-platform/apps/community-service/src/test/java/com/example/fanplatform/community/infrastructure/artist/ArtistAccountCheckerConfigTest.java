package com.example.fanplatform.community.infrastructure.artist;

import com.example.fanplatform.community.domain.follow.ArtistAccountChecker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bean-selection pin for {@link ArtistAccountCheckerConfig} — TASK-FAN-BE-045 AC-7,
 * <strong>rewritten by TASK-FAN-INT-005 AC-3</strong>.
 *
 * <h2>What changed, and why the cases were inverted rather than deleted</h2>
 *
 * The opt-out this class used to pin is <b>gone</b>. {@code ADR-004}'s ACCEPT allowed
 * an e2e escape hatch only because the live-trio had no IAM to mint a
 * {@code client_credentials} token from; the trio has one now
 * ({@code FanPlatformE2ETestBase}), so the condition the hatch was granted under no
 * longer holds and the permissive bean has been removed.
 *
 * <p>🔴 The two "off" cases are <b>inverted, not removed</b>. Deleting them would take
 * the whole axis out of the audit: nothing would then fail if someone re-added an
 * opt-out, because no test would ever again set {@code community.artist-service.enabled}
 * to anything. Inverted, they assert the stronger fact — <em>that property no longer
 * has an off position</em> — and they fail the moment one is reintroduced.
 *
 * <p>Docker-free {@link ApplicationContextRunner}, so this runs in
 * {@code :community-service:check} at PR time rather than only in the nightly
 * Testcontainers suite.
 */
class ArtistAccountCheckerConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ArtistAccountCheckerConfig.class);

    @Test
    @DisplayName("property absent (production default) -> the real HTTP checker, validation ON")
    void defaultSelectsHttpChecker() {
        runner.run(ctx -> assertThat(ctx.getBean(ArtistAccountChecker.class))
                .isInstanceOf(HttpArtistAccountChecker.class));
    }

    @Test
    @DisplayName("enabled=true -> the real HTTP checker")
    void enabledTrueSelectsHttpChecker() {
        runner.withPropertyValues("community.artist-service.enabled=true")
                .run(ctx -> assertThat(ctx.getBean(ArtistAccountChecker.class))
                        .isInstanceOf(HttpArtistAccountChecker.class));
    }

    /**
     * INVERTED (was: "enabled=false -> the unverified checker"). The value that used
     * to buy a caller out of follow-target validation now buys nothing.
     */
    @Test
    @DisplayName("enabled=false NO LONGER opts out — the real checker is selected anyway")
    void disabledStillSelectsHttpChecker() {
        runner.withPropertyValues("community.artist-service.enabled=false")
                .run(ctx -> assertThat(ctx.getBean(ArtistAccountChecker.class))
                        .as("TASK-FAN-INT-005 deleted UnverifiedArtistAccountChecker; "
                                + "no property value may turn this gate off")
                        .isInstanceOf(HttpArtistAccountChecker.class));
    }

    /**
     * INVERTED (was: "FALSE also selects the opt-out — havingValue is case-insensitive").
     *
     * <p>Kept as its own case because the original was itself a correction: the first
     * version asserted "only the exact string {@code false}" and {@code FALSE} failed
     * it, since {@code havingValue} compares case-insensitively. That measurement is
     * still worth carrying — if an opt-out is ever reintroduced, whoever writes it
     * will re-learn the same thing, and this case is where it is written down.
     */
    @Test
    @DisplayName("FALSE (any case) is equally inert now")
    void falseInAnyCaseIsInert() {
        for (String value : new String[] {"false", "FALSE", "False"}) {
            runner.withPropertyValues("community.artist-service.enabled=" + value)
                    .run(ctx -> assertThat(ctx.getBean(ArtistAccountChecker.class))
                            .as("value=%s", value)
                            .isInstanceOf(HttpArtistAccountChecker.class));
        }
    }

    /**
     * Every unanticipated configuration lands on validation-ON. This case predates
     * the hatch removal and survives it unchanged, because the shape it guards is
     * unchanged: the real checker is the {@code @ConditionalOnMissingBean} fallback.
     *
     * <p>Its history is worth keeping. The first implementation gave the real checker
     * {@code @ConditionalOnProperty(havingValue="true", matchIfMissing=true)}.
     * {@code matchIfMissing} applies only when the property is <em>absent</em>, so a
     * present-but-other value matched <b>neither</b> bean — zero
     * {@link ArtistAccountChecker} beans and a context that will not refresh.
     */
    @Test
    @DisplayName("any other value selects the real checker")
    void nonFalseValuesKeepValidationOn() {
        for (String value : new String[] {"no", "TRUE", "1", "yes", ""}) {
            runner.withPropertyValues("community.artist-service.enabled=" + value)
                    .run(ctx -> assertThat(ctx.getBean(ArtistAccountChecker.class))
                            .as("value=%s", value)
                            .isInstanceOf(HttpArtistAccountChecker.class));
        }
    }

    @Test
    @DisplayName("a misspelled property key selects the real checker")
    void misspelledKeyKeepsValidationOn() {
        runner.withPropertyValues("community.artist-service.enabledd=false")
                .run(ctx -> assertThat(ctx.getBean(ArtistAccountChecker.class))
                        .isInstanceOf(HttpArtistAccountChecker.class));
    }

    @Test
    @DisplayName("exactly one checker bean in every configuration")
    void exactlyOneCheckerBean() {
        runner.run(ctx ->
                assertThat(ctx.getBeanNamesForType(ArtistAccountChecker.class)).hasSize(1));
        runner.withPropertyValues("community.artist-service.enabled=false").run(ctx ->
                assertThat(ctx.getBeanNamesForType(ArtistAccountChecker.class)).hasSize(1));
    }

    /**
     * TASK-FAN-INT-005 AC-3 — the structural half of "the hatch cannot come back".
     *
     * <p>The behavioural cases above all drive the context through one property, so
     * they only catch an opt-out that is reachable <em>by that property</em>. An
     * opt-out reintroduced behind a different key, a profile, or a plain
     * {@code @ConditionalOnMissingClass} would leave every one of them green. This
     * case asserts the cardinality of the declaration itself: this configuration
     * contributes exactly ONE {@link ArtistAccountChecker} bean method, so any second
     * one fails here regardless of how it is conditioned.
     */
    @Test
    @DisplayName("the configuration declares exactly one ArtistAccountChecker @Bean method")
    void configurationDeclaresASingleCheckerBeanMethod() {
        List<String> beanMethods = Arrays.stream(ArtistAccountCheckerConfig.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Bean.class))
                .filter(m -> ArtistAccountChecker.class.isAssignableFrom(m.getReturnType()))
                .map(Method::getName)
                .sorted()
                .toList();
        assertThat(beanMethods)
                .as("a second checker @Bean means an escape hatch is back — "
                        + "TASK-FAN-INT-005 removed the last one when iam joined the live e2e stack")
                .containsExactly("httpArtistAccountChecker");
    }
}
