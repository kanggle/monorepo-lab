package com.example.fanplatform.membership.domain.access;

import com.example.fanplatform.membership.domain.membership.Membership;
import com.example.fanplatform.membership.domain.membership.MembershipTier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AccessPolicyTest {

    private static final Instant NOW = Instant.parse("2026-06-09T00:00:00Z");

    private static Membership active(MembershipTier tier, Instant from, Instant to) {
        return Membership.activate("m1", "fan-platform", "acc1", tier, from, to, 1, "pgmock_x", from);
    }

    // ----- tierGrants matrix -----------------------------------------------

    @Test
    @DisplayName("PREMIUM grants both PREMIUM and MEMBERS_ONLY")
    void premiumGrantsBoth() {
        assertThat(AccessPolicy.tierGrants(MembershipTier.PREMIUM, MembershipTier.PREMIUM)).isTrue();
        assertThat(AccessPolicy.tierGrants(MembershipTier.PREMIUM, MembershipTier.MEMBERS_ONLY)).isTrue();
    }

    @Test
    @DisplayName("MEMBERS_ONLY grants only MEMBERS_ONLY (PREMIUM denied)")
    void membersOnlyGrantsOnlyMembers() {
        assertThat(AccessPolicy.tierGrants(MembershipTier.MEMBERS_ONLY, MembershipTier.MEMBERS_ONLY)).isTrue();
        assertThat(AccessPolicy.tierGrants(MembershipTier.MEMBERS_ONLY, MembershipTier.PREMIUM)).isFalse();
    }

    @Test
    @DisplayName("null tiers deny (fail-closed)")
    void nullTiersDeny() {
        assertThat(AccessPolicy.tierGrants(null, MembershipTier.MEMBERS_ONLY)).isFalse();
        assertThat(AccessPolicy.tierGrants(MembershipTier.PREMIUM, null)).isFalse();
    }

    // ----- window ----------------------------------------------------------

    @Test
    @DisplayName("inWindow true at boundaries and inside; false past validTo")
    void windowEvaluation() {
        Instant from = NOW.minus(10, ChronoUnit.DAYS);
        Instant to = NOW.plus(10, ChronoUnit.DAYS);
        Membership m = active(MembershipTier.PREMIUM, from, to);

        assertThat(AccessPolicy.inWindow(m, NOW)).isTrue();
        assertThat(AccessPolicy.inWindow(m, from)).isTrue();   // inclusive lower bound
        assertThat(AccessPolicy.inWindow(m, to)).isTrue();     // inclusive upper bound
        assertThat(AccessPolicy.inWindow(m, to.plusSeconds(1))).isFalse();
        assertThat(AccessPolicy.inWindow(m, from.minusSeconds(1))).isFalse();
    }

    // ----- grantsAccess (full evaluation) ----------------------------------

    @Test
    @DisplayName("PREMIUM ACTIVE in-window grants MEMBERS_ONLY content")
    void premiumGrantsMembersInWindow() {
        Membership m = active(MembershipTier.PREMIUM, NOW.minus(1, ChronoUnit.DAYS), NOW.plus(1, ChronoUnit.DAYS));
        assertThat(AccessPolicy.grantsAccess(m, MembershipTier.MEMBERS_ONLY, NOW)).isTrue();
    }

    @Test
    @DisplayName("MEMBERS_ONLY ACTIVE in-window denies PREMIUM content")
    void membersDeniesPremium() {
        Membership m = active(MembershipTier.MEMBERS_ONLY, NOW.minus(1, ChronoUnit.DAYS), NOW.plus(1, ChronoUnit.DAYS));
        assertThat(AccessPolicy.grantsAccess(m, MembershipTier.PREMIUM, NOW)).isFalse();
    }

    @Test
    @DisplayName("expired (now > validTo) denies even matching tier")
    void expiredDenies() {
        Membership m = active(MembershipTier.PREMIUM, NOW.minus(40, ChronoUnit.DAYS), NOW.minus(10, ChronoUnit.DAYS));
        assertThat(AccessPolicy.grantsAccess(m, MembershipTier.PREMIUM, NOW)).isFalse();
    }
}
