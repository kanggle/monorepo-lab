package com.example.fanplatform.membership.application;

import com.example.fanplatform.membership.application.event.MembershipEventPublisher;
import com.example.fanplatform.membership.domain.membership.Membership;
import com.example.fanplatform.membership.domain.membership.MembershipRepository;
import com.example.fanplatform.membership.domain.membership.MembershipTier;
import com.example.fanplatform.membership.domain.membership.status.MembershipStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SweepExpiredMembershipsUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-07-12T00:00:00Z");

    private MembershipRepository repository;
    private MembershipEventPublisher publisher;
    private SweepExpiredMembershipsUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = mock(MembershipRepository.class);
        publisher = mock(MembershipEventPublisher.class);
        useCase = new SweepExpiredMembershipsUseCase(repository, publisher, () -> NOW);
    }

    private static Membership pastWindow(String id) {
        Instant validFrom = NOW.minus(40, ChronoUnit.DAYS);
        Instant validTo = NOW.minus(1, ChronoUnit.DAYS);
        return Membership.activate(id, "fan-platform", "acc-" + id, MembershipTier.MEMBERS_ONLY,
                validFrom, validTo, 1, "pay-ref", validFrom);
    }

    @Test
    @DisplayName("marks each expirable membership and emits one expired event per membership")
    void sweepMarksAndEmits() {
        Membership m1 = pastWindow("m1");
        Membership m2 = pastWindow("m2");
        when(repository.findExpirable(NOW, 100)).thenReturn(List.of(m1, m2));

        int swept = useCase.sweep(100);

        assertThat(swept).isEqualTo(2);
        assertThat(m1.isExpiryNotified()).isTrue();
        assertThat(m1.getExpiryNotifiedAt()).isEqualTo(NOW);
        assertThat(m2.isExpiryNotified()).isTrue();
        verify(publisher).publishExpired(eq("m1"), eq("fan-platform"), eq("acc-m1"),
                eq(MembershipTier.MEMBERS_ONLY), eq(m1.getValidTo()), eq(NOW));
        verify(publisher).publishExpired(eq("m2"), eq("fan-platform"), eq("acc-m2"),
                eq(MembershipTier.MEMBERS_ONLY), eq(m2.getValidTo()), eq(NOW));
        // Status is unchanged — no stored EXPIRED (Option B).
        assertThat(m1.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
    }

    @Test
    @DisplayName("empty candidate set → no events, returns 0")
    void sweepEmptyIsNoOp() {
        when(repository.findExpirable(NOW, 100)).thenReturn(List.of());

        int swept = useCase.sweep(100);

        assertThat(swept).isZero();
        verifyNoInteractions(publisher);
    }
}
