package com.example.fanplatform.community.infrastructure.artist;

import com.example.fanplatform.community.domain.follow.ArtistAccountChecker;
import lombok.extern.slf4j.Slf4j;

/**
 * The e2e escape hatch TASK-FAN-BE-045 AC-7 answers with — <b>reachable only by
 * setting {@code community.artist-service.enabled=false} explicitly</b>.
 *
 * <h2>Why the name is ugly</h2>
 *
 * It is called <i>Unverified</i>, not <i>AlwaysAllow</i>, because that is what it
 * does to the product: with this bean selected, {@code follows.artist_account_id}
 * goes back to being an unvalidated free string and the feed join holds only by
 * coincidence. Reading the bean name in a startup log should be alarming.
 *
 * <h2>Why it is not the {@code AlwaysAllowMembershipChecker} shape</h2>
 *
 * Its sibling is selected by {@code @ConditionalOnMissingBean}, so a bean-ordering
 * slip can select it <em>by accident</em> and the service then ships with the gate
 * silently off — the failure {@code ADR-004} § Decision Drivers 3 names. This one
 * is bound to {@code havingValue="false"} with no {@code matchIfMissing}: absence
 * of configuration selects the real checker, and nothing but an explicit,
 * non-default property value can reach this class.
 * {@code ArtistAccountCheckerConfigTest} pins that default so a flip fails a test
 * rather than a demo.
 */
@Slf4j
public class UnverifiedArtistAccountChecker implements ArtistAccountChecker {

    public UnverifiedArtistAccountChecker() {
        log.warn("community.artist-service.enabled=false — follow targets are NOT validated. "
                + "follows.artist_account_id accepts any string and the feed join is "
                + "coincidental. This must never be set in production.");
    }

    @Override
    public boolean isArtistAccount(String accountId, String tenantId) {
        return true;
    }
}
