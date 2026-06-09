package com.example.fanplatform.membership.application;

import com.example.fanplatform.membership.domain.access.AccessPolicy;
import com.example.fanplatform.membership.domain.membership.Membership;
import com.example.fanplatform.membership.domain.membership.MembershipRepository;
import com.example.fanplatform.membership.domain.membership.MembershipTier;
import com.example.fanplatform.membership.domain.time.ClockPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Computes {@code hasAccess(accountId, requiredTier, tenantId)} — the remote
 * counterpart of community-service's
 * {@code MembershipChecker.hasAccess(accountId, tier, tenantId)}.
 *
 * <p>Returns {@code true} iff there EXISTS a membership for {@code (accountId,
 * tenantId)} that is {@code status == ACTIVE} AND {@code now ∈ [validFrom,
 * validTo]} AND {@code tierGrants(membership.tier, requiredTier)} (PREMIUM ⊇
 * MEMBERS_ONLY). Otherwise {@code false}.
 *
 * <p><strong>Fail-closed.</strong> Any infrastructure error (DB unavailable,
 * query failure) is caught and yields {@code false} (DENY), never {@code true}.
 * An unknown {@code requiredTier} string is also DENY. A domain "deny" (no
 * membership, expired, canceled, insufficient tier, cross-tenant) is a normal
 * {@code false}, NOT an error.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckAccessUseCase {

    private final MembershipRepository membershipRepository;
    private final ClockPort clock;

    /**
     * @param accountId    the fan whose access is being checked
     * @param requiredTier the required tier of the gated content ({@code MEMBERS_ONLY} | {@code PREMIUM})
     * @param tenantId     tenant scope
     * @return {@code true} to grant, {@code false} to deny (fail-closed on error)
     */
    @Transactional(readOnly = true)
    public boolean hasAccess(String accountId, String requiredTier, String tenantId) {
        MembershipTier tier;
        try {
            tier = MembershipTier.valueOf(requiredTier);
        } catch (IllegalArgumentException | NullPointerException e) {
            // Unknown required tier → deny (do not leak as an error).
            log.debug("access-check denied: unknown required tier '{}'", requiredTier);
            return false;
        }

        try {
            Instant now = clock.now();
            List<Membership> candidates = membershipRepository.findActiveByAccount(accountId, tenantId);
            return candidates.stream().anyMatch(m -> AccessPolicy.grantsAccess(m, tier, now));
        } catch (RuntimeException e) {
            // Fail-closed: infrastructure error → DENY (never ALLOW on error).
            log.warn("access-check fail-closed (deny) for account={} tenant={} tier={}: {}",
                    accountId, tenantId, requiredTier, e.toString());
            return false;
        }
    }
}
