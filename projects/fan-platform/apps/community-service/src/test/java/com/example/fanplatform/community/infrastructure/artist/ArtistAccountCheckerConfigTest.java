package com.example.fanplatform.community.infrastructure.artist;

import com.example.fanplatform.community.domain.follow.ArtistAccountChecker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bean-selection pin for {@link ArtistAccountCheckerConfig} — TASK-FAN-BE-045 AC-7.
 *
 * <p>This is the test the AC-7 answer leans on. {@code ADR-004}'s ACCEPT allowed an
 * e2e escape hatch only if <strong>the default is the refusing side</strong>, so the
 * default must be pinned somewhere that fails a build rather than a demo. Docker-free
 * {@link ApplicationContextRunner}, so it runs in {@code :community-service:check} at
 * PR time rather than only in the nightly Testcontainers suite.
 *
 * <p>The hazard being guarded is specific: the sibling
 * {@code AlwaysAllowMembershipChecker} is selected by
 * {@code @ConditionalOnMissingBean}, which a bean-ordering slip can trip
 * <em>accidentally</em> — the service then runs with the gate off and everything is
 * green ({@code ADR-004} § Decision Drivers 3). The permissive checker here is
 * reachable only through {@code havingValue="false"}, and the cases below state that
 * as three separate facts rather than one.
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

    @Test
    @DisplayName("enabled=false -> the unverified checker (the explicit live-trio opt-out)")
    void disabledSelectsUnverifiedChecker() {
        runner.withPropertyValues("community.artist-service.enabled=false")
                .run(ctx -> assertThat(ctx.getBean(ArtistAccountChecker.class))
                        .isInstanceOf(UnverifiedArtistAccountChecker.class));
    }

    /**
     * Every unanticipated configuration must land on validation-ON, and this case
     * is why the config is shaped the way it is.
     *
     * <p>The first implementation gave the real checker
     * {@code @ConditionalOnProperty(havingValue="true", matchIfMissing=true)}.
     * {@code matchIfMissing} applies only when the property is <em>absent</em>, so
     * a present-but-other value matched <b>neither</b> bean — zero
     * {@link ArtistAccountChecker} beans and a context that will not refresh. The
     * gate never opened, but {@code COMMUNITY_ARTIST_SERVICE_ENABLED=TRUE} would
     * have bricked the service. Making the real checker the
     * {@code @ConditionalOnMissingBean} fallback is what fixed it, and these
     * values are the ones that prove it.
     */
    @Test
    @DisplayName("any value other than false/FALSE selects the real checker")
    void nonFalseValuesKeepValidationOn() {
        for (String value : new String[] {"no", "TRUE", "1", "yes", ""}) {
            runner.withPropertyValues("community.artist-service.enabled=" + value)
                    .run(ctx -> assertThat(ctx.getBean(ArtistAccountChecker.class))
                            .as("value=%s", value)
                            .isInstanceOf(HttpArtistAccountChecker.class));
        }
    }

    /**
     * {@code havingValue} compares case-insensitively, so {@code FALSE} disables
     * too. Measured, not assumed — the first version of the case above asserted
     * "only the exact string {@code false}" and {@code FALSE} failed it. Stated as
     * its own case so the opt-out's real surface is written down rather than
     * discovered by whoever next types it in upper case.
     */
    @Test
    @DisplayName("FALSE (any case) also selects the opt-out — havingValue is case-insensitive")
    void falseIsMatchedCaseInsensitively() {
        runner.withPropertyValues("community.artist-service.enabled=FALSE")
                .run(ctx -> assertThat(ctx.getBean(ArtistAccountChecker.class))
                        .isInstanceOf(UnverifiedArtistAccountChecker.class));
    }

    /**
     * A misspelled key leaves the real key absent, which must also validate.
     * Separate from the case above because it is a different failure (wrong key
     * vs wrong value) and lumping them would let one cover for the other.
     */
    @Test
    @DisplayName("a misspelled property key selects the real checker")
    void misspelledKeyKeepsValidationOn() {
        runner.withPropertyValues("community.artist-service.enabledd=false")
                .run(ctx -> assertThat(ctx.getBean(ArtistAccountChecker.class))
                        .isInstanceOf(HttpArtistAccountChecker.class));
    }

    /**
     * Exactly one {@link ArtistAccountChecker} is ever in the context. If both
     * conditions could hold at once, injection into {@code FollowArtistUseCase}
     * would fail at refresh — but a future edit that made them overlap with one
     * marked {@code @Primary} would silently pick a winner instead, so state the
     * cardinality out loud.
     */
    @Test
    @DisplayName("exactly one checker bean in every configuration")
    void exactlyOneCheckerBean() {
        runner.run(ctx ->
                assertThat(ctx.getBeanNamesForType(ArtistAccountChecker.class)).hasSize(1));
        runner.withPropertyValues("community.artist-service.enabled=false").run(ctx ->
                assertThat(ctx.getBeanNamesForType(ArtistAccountChecker.class)).hasSize(1));
    }
}
