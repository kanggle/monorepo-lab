package com.example.fanplatform.membership.application;

import com.example.fanplatform.membership.domain.access.AccessPolicy;
import com.example.fanplatform.membership.domain.membership.Membership;
import com.example.fanplatform.membership.domain.membership.MembershipRepository;
import com.example.fanplatform.membership.domain.membership.MembershipTier;
import com.example.fanplatform.membership.domain.pricing.MembershipPricing;
import com.example.fanplatform.membership.domain.pricing.UpgradeProration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Optional;

/**
 * Single source of truth for MEMBERS_ONLY → PREMIUM upgrade assessment
 * (TASK-FAN-BE-032). Both {@link SubscribeUseCase} (which charges + supersedes) and
 * {@code QuoteUpgradeUseCase} (which previews the price) call {@link #assess} so the
 * amount the client is quoted and the amount the backend re-verifies come from the
 * SAME formula — a divergence would wrongfully decline the PortOne payment
 * (BE-032 § Failure Scenarios).
 */
@Component
@RequiredArgsConstructor
public class UpgradeQuoter {

    private final MembershipRepository membershipRepository;

    /**
     * @param supersedes the ACTIVE, in-window MEMBERS_ONLY membership this PREMIUM
     *                   subscribe would cancel (empty = plain subscribe, no credit)
     * @param quote      list price / credit / charge for the request
     */
    public record UpgradeAssessment(Optional<Membership> supersedes, UpgradeProration.Quote quote) {
    }

    public UpgradeAssessment assess(MembershipTier tier, int planMonths,
                                    String accountId, String tenantId, Instant now) {
        Optional<Membership> source = upgradeSource(tier, accountId, tenantId, now);
        long listPrice = MembershipPricing.listChargeMinor(tier, planMonths);
        UpgradeProration.Quote quote = source
                .map(m -> UpgradeProration.quote(
                        ChronoUnit.DAYS.between(now, m.getValidTo()),
                        planMonths,
                        MembershipPricing.PREMIUM_MONTHLY_MINOR,
                        MembershipPricing.MEMBERS_ONLY_MONTHLY_MINOR))
                .orElseGet(() -> new UpgradeProration.Quote(listPrice, 0L, listPrice, 0L));
        return new UpgradeAssessment(source, quote);
    }

    /**
     * The members-only membership a PREMIUM subscribe upgrades from: the ACTIVE,
     * read-time in-window MEMBERS_ONLY row with the most remaining window (largest
     * credit). Empty for a non-PREMIUM target or when none is held.
     */
    private Optional<Membership> upgradeSource(MembershipTier tier, String accountId,
                                               String tenantId, Instant now) {
        if (tier != MembershipTier.PREMIUM) {
            return Optional.empty();
        }
        return membershipRepository.findActiveByAccount(accountId, tenantId).stream()
                .filter(m -> m.getTier() == MembershipTier.MEMBERS_ONLY)
                .filter(m -> AccessPolicy.inWindow(m, now))
                .max(Comparator.comparing(Membership::getValidTo));
    }
}
