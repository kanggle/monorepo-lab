package com.example.fanplatform.community.integration;

import com.example.fanplatform.community.domain.follow.ArtistAccountChecker;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test-only {@link ArtistAccountChecker} that confirms every target in this
 * tenant — the sanctioned {@code @Primary @TestConfiguration} seam named in
 * {@code ArtistAccountCheckerConfig}'s Javadoc.
 *
 * <h2>Scope — read before reusing</h2>
 *
 * This exists ONLY for suites whose subject is something else and which happen to
 * follow an artist on the way there (envelope shape, follow/unfollow round trip).
 * Before TASK-FAN-BE-045 those suites could follow any string; now every follow
 * goes through artist-service, which is a different service with a different
 * database and is not in this suite's compose.
 *
 * <p>🔴 It must NOT be imported by a test whose subject IS the follow gate. An
 * always-confirm checker makes a disabled validation look exactly like a working
 * one — the failure mode {@code ADR-004} § Decision Drivers 3 names. That risk grew
 * rather than shrank: TASK-FAN-INT-005 deleted production's same-shaped bean
 * ({@code UnverifiedArtistAccountChecker}, which was reachable via
 * {@code community.artist-service.enabled=false}), so this class is now the ONLY
 * accept-everything {@code ArtistAccountChecker} left anywhere, and a stray
 * {@code @Import} is the only remaining way to switch the gate off. That is
 * precisely why no gate test may get its verdict from a permissive checker. The
 * gate's own
 * assertions live in {@code FollowArtistGateIntegrationTest} (real
 * {@code HttpArtistAccountChecker}, stubbed far side, including the unreachable
 * case) and {@code ArtistPostReachesFollowerFeedIntegrationTest} (a whitelist
 * checker that still denies unregistered targets).
 */
@TestConfiguration
public class ConfirmAllArtistAccountsTestConfig {

    @Bean
    @Primary
    ArtistAccountChecker confirmAllArtistAccountChecker() {
        return (accountId, tenantId) -> "fan-platform".equals(tenantId)
                && accountId != null && !accountId.isBlank();
    }
}
