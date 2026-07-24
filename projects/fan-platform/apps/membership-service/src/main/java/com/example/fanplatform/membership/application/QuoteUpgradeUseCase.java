package com.example.fanplatform.membership.application;

import com.example.fanplatform.membership.domain.membership.Membership;
import com.example.fanplatform.membership.domain.membership.MembershipTier;
import com.example.fanplatform.membership.domain.time.ClockPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Preview the price of a subscribe/upgrade BEFORE the client opens the PortOne
 * payment window (TASK-FAN-BE-032). Returns the plain tier list price, or — when a
 * PREMIUM subscribe would upgrade from an active MEMBERS_ONLY membership — the
 * prorated charge + credit + the members-only id that would be superseded. Uses the
 * SAME {@link UpgradeQuoter} the subscribe path charges against, so the previewed
 * {@code chargeMinor} is exactly what the backend re-verifies.
 */
@Service
@RequiredArgsConstructor
public class QuoteUpgradeUseCase {

    private final UpgradeQuoter upgradeQuoter;
    private final ClockPort clock;

    @Transactional(readOnly = true)
    public UpgradeQuoteView execute(ActorContext actor, MembershipTier tier, int planMonths) {
        int safeMonths = Math.max(1, planMonths);
        Instant now = clock.now();
        UpgradeQuoter.UpgradeAssessment a =
                upgradeQuoter.assess(tier, safeMonths, actor.accountId(), actor.tenantId(), now);
        String supersedesId = a.supersedes().map(Membership::getId).orElse(null);
        return new UpgradeQuoteView(
                tier, safeMonths,
                a.quote().listPriceMinor(), a.quote().creditMinor(), a.quote().chargeMinor(),
                supersedesId);
    }
}
