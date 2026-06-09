package com.example.fanplatform.membership.application;

import com.example.fanplatform.membership.domain.membership.Membership;
import com.example.fanplatform.membership.domain.membership.MembershipRepository;
import com.example.fanplatform.membership.domain.membership.MembershipTier;
import com.example.fanplatform.membership.domain.time.ClockPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class CheckAccessUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-06-09T00:00:00Z");

    @Mock MembershipRepository membershipRepository;
    @Mock ClockPort clock;

    @InjectMocks CheckAccessUseCase useCase;

    private Membership active(MembershipTier tier, Instant from, Instant to) {
        return Membership.activate("m1", "fan-platform", "acc1", tier, from, to, 1, "pgmock_x", from);
    }

    @Test
    @DisplayName("PREMIUM ACTIVE in-window grants MEMBERS_ONLY")
    void premiumGrantsMembers() {
        when(clock.now()).thenReturn(NOW);
        when(membershipRepository.findActiveByAccount("acc1", "fan-platform"))
                .thenReturn(List.of(active(MembershipTier.PREMIUM,
                        NOW.minus(1, ChronoUnit.DAYS), NOW.plus(1, ChronoUnit.DAYS))));

        assertThat(useCase.hasAccess("acc1", "MEMBERS_ONLY", "fan-platform")).isTrue();
    }

    @Test
    @DisplayName("MEMBERS_ONLY ACTIVE denies PREMIUM")
    void membersDeniesPremium() {
        when(clock.now()).thenReturn(NOW);
        when(membershipRepository.findActiveByAccount("acc1", "fan-platform"))
                .thenReturn(List.of(active(MembershipTier.MEMBERS_ONLY,
                        NOW.minus(1, ChronoUnit.DAYS), NOW.plus(1, ChronoUnit.DAYS))));

        assertThat(useCase.hasAccess("acc1", "PREMIUM", "fan-platform")).isFalse();
    }

    @Test
    @DisplayName("expired window denies")
    void expiredDenies() {
        when(clock.now()).thenReturn(NOW);
        when(membershipRepository.findActiveByAccount("acc1", "fan-platform"))
                .thenReturn(List.of(active(MembershipTier.PREMIUM,
                        NOW.minus(40, ChronoUnit.DAYS), NOW.minus(10, ChronoUnit.DAYS))));

        assertThat(useCase.hasAccess("acc1", "PREMIUM", "fan-platform")).isFalse();
    }

    @Test
    @DisplayName("no membership row denies")
    void noRowDenies() {
        when(clock.now()).thenReturn(NOW);
        when(membershipRepository.findActiveByAccount("acc1", "fan-platform")).thenReturn(List.of());

        assertThat(useCase.hasAccess("acc1", "MEMBERS_ONLY", "fan-platform")).isFalse();
    }

    @Test
    @DisplayName("unknown required tier denies (not an error)")
    void unknownTierDenies() {
        // No repository interaction — unknown tier short-circuits before query.
        assertThat(useCase.hasAccess("acc1", "GOLD", "fan-platform")).isFalse();
    }

    @Test
    @DisplayName("DB error → fail-closed deny (never throws)")
    void dbErrorFailsClosed() {
        lenient().when(clock.now()).thenReturn(NOW);
        when(membershipRepository.findActiveByAccount("acc1", "fan-platform"))
                .thenThrow(new RuntimeException("DB down"));

        assertThat(useCase.hasAccess("acc1", "MEMBERS_ONLY", "fan-platform")).isFalse();
    }
}
