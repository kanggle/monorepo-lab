package com.example.fanplatform.membership.application;

import com.example.fanplatform.membership.application.event.MembershipEventPublisher;
import com.example.fanplatform.membership.application.exception.MembershipNotFoundException;
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
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class CancelMembershipUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-06-09T00:00:00Z");
    private static final ActorContext ACTOR = new ActorContext("acc1", "fan-platform", Set.of("FAN"));

    @Mock MembershipRepository membershipRepository;
    @Mock MembershipEventPublisher eventPublisher;
    @Mock ClockPort clock;

    @InjectMocks CancelMembershipUseCase useCase;

    private Membership active() {
        return Membership.activate("m1", "fan-platform", "acc1", MembershipTier.PREMIUM,
                NOW.minusSeconds(10), NOW.plusSeconds(1000), 1, "pgmock_x", NOW.minusSeconds(10));
    }

    @Test
    @DisplayName("ACTIVE → CANCELED sets canceledAt + emits canceled event")
    void cancelActive() {
        when(clock.now()).thenReturn(NOW);
        Membership m = active();
        when(membershipRepository.findByIdScoped("m1", "acc1", "fan-platform")).thenReturn(Optional.of(m));
        when(membershipRepository.save(any(Membership.class))).thenAnswer(inv -> inv.getArgument(0));

        MembershipView view = useCase.execute("m1", ACTOR, "no longer needed");

        assertThat(view.status().name()).isEqualTo("CANCELED");
        assertThat(view.canceledAt()).isEqualTo(NOW);
        verify(eventPublisher).publishCanceled(eq("m1"), eq("fan-platform"), eq("acc1"),
                eq(MembershipTier.PREMIUM), eq("no longer needed"), eq(NOW), eq(NOW));
    }

    @Test
    @DisplayName("re-cancel of CANCELED → idempotent no-op, NO event")
    void recancelIdempotent() {
        when(clock.now()).thenReturn(NOW);
        Membership m = active();
        m.cancel(NOW.minusSeconds(5)); // already CANCELED
        when(membershipRepository.findByIdScoped("m1", "acc1", "fan-platform")).thenReturn(Optional.of(m));

        MembershipView view = useCase.execute("m1", ACTOR, "again");

        assertThat(view.status().name()).isEqualTo("CANCELED");
        verify(membershipRepository, never()).save(any());
        verify(eventPublisher, never()).publishCanceled(anyString(), anyString(), anyString(),
                any(), anyString(), any(), any());
    }

    @Test
    @DisplayName("unknown / cross-account / cross-tenant id → 404")
    void notFound() {
        when(membershipRepository.findByIdScoped("missing", "acc1", "fan-platform"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("missing", ACTOR, null))
                .isInstanceOf(MembershipNotFoundException.class);

        verify(membershipRepository, never()).save(any());
    }
}
