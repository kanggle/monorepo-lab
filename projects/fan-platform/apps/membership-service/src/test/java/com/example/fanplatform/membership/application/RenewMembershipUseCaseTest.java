package com.example.fanplatform.membership.application;

import com.example.fanplatform.membership.application.event.MembershipEventPublisher;
import com.example.fanplatform.membership.application.exception.MembershipNotFoundException;
import com.example.fanplatform.membership.application.exception.MembershipNotRenewableException;
import com.example.fanplatform.membership.application.exception.PaymentDeclinedException;
import com.example.fanplatform.membership.domain.idempotency.IdempotencyKeyRepository;
import com.example.fanplatform.membership.domain.membership.Membership;
import com.example.fanplatform.membership.domain.membership.MembershipRepository;
import com.example.fanplatform.membership.domain.membership.MembershipTier;
import com.example.fanplatform.membership.domain.time.ClockPort;
import com.example.libs.payment.PaymentAuthorization;
import com.example.libs.payment.PaymentGatewayPort;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class RenewMembershipUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-07-12T00:00:00Z");
    private static final ActorContext ACTOR =
            new ActorContext("acc1", "fan-platform", java.util.Set.of("FAN"));

    @Mock MembershipRepository membershipRepository;
    @Mock IdempotencyKeyRepository idempotencyKeyRepository;
    @Mock PaymentGatewayPort paymentGateway;
    @Mock MembershipEventPublisher eventPublisher;
    @Mock ClockPort clock;

    @InjectMocks RenewMembershipUseCase useCase;

    private RenewCommand cmd(String priorId, String key) {
        return new RenewCommand(ACTOR, priorId, 1, "tok_visa_demo", key);
    }

    private static Membership prior(String id, Instant validFrom, Instant validTo) {
        return Membership.activate(id, "fan-platform", "acc1", MembershipTier.PREMIUM,
                validFrom, validTo, 1, "pgmock_old", validFrom);
    }

    @Test
    @DisplayName("active prior → seamless window (validFrom = prior.validTo), same tier, activated event")
    void renewActiveSeamless() {
        Instant priorValidTo = NOW.plus(10, ChronoUnit.DAYS);
        Membership p = prior("m0", NOW.minus(20, ChronoUnit.DAYS), priorValidTo);
        when(membershipRepository.findByIdScoped("m0", "acc1", "fan-platform")).thenReturn(Optional.of(p));
        when(idempotencyKeyRepository.find("fan-platform", "acc1", "k1")).thenReturn(Optional.empty());
        when(clock.now()).thenReturn(NOW);
        // The renew charge is verified against the token; amount is the tier list price (not
        // pinned here, matching the old anyLong() matcher) — match on paymentReference.
        when(paymentGateway.verify(argThat(r -> "tok_visa_demo".equals(r.paymentReference()))))
                .thenReturn(PaymentAuthorization.approved("pgmock_new", null, null));
        when(membershipRepository.save(any(Membership.class))).thenAnswer(inv -> inv.getArgument(0));

        MembershipView view = useCase.execute(cmd("m0", "k1"));

        assertThat(view.status().name()).isEqualTo("ACTIVE");
        assertThat(view.tier()).isEqualTo(MembershipTier.PREMIUM);
        assertThat(view.validFrom()).isEqualTo(priorValidTo); // seamless stacking — no lost days
        assertThat(view.validTo()).isEqualTo(priorValidTo.plus(30, ChronoUnit.DAYS));
        verify(eventPublisher).publishActivated(anyString(), eq("fan-platform"), eq("acc1"),
                eq(MembershipTier.PREMIUM), eq(1), eq(priorValidTo), any(Instant.class), eq(NOW));
    }

    @Test
    @DisplayName("expired prior → fresh window (validFrom = now)")
    void renewExpiredFreshWindow() {
        Membership p = prior("m0", NOW.minus(40, ChronoUnit.DAYS), NOW.minus(1, ChronoUnit.DAYS));
        when(membershipRepository.findByIdScoped("m0", "acc1", "fan-platform")).thenReturn(Optional.of(p));
        when(idempotencyKeyRepository.find("fan-platform", "acc1", "k1")).thenReturn(Optional.empty());
        when(clock.now()).thenReturn(NOW);
        when(paymentGateway.verify(any()))
                .thenReturn(PaymentAuthorization.approved("pgmock_new", null, null));
        when(membershipRepository.save(any(Membership.class))).thenAnswer(inv -> inv.getArgument(0));

        MembershipView view = useCase.execute(cmd("m0", "k1"));

        assertThat(view.validFrom()).isEqualTo(NOW);
        assertThat(view.validTo()).isEqualTo(NOW.plus(30, ChronoUnit.DAYS));
    }

    @Test
    @DisplayName("canceled prior → 422 MembershipNotRenewable, no row, no event")
    void renewCanceledRejected() {
        Membership p = prior("m0", NOW.minus(40, ChronoUnit.DAYS), NOW.minus(1, ChronoUnit.DAYS));
        p.cancel(NOW.minus(5, ChronoUnit.DAYS));
        when(membershipRepository.findByIdScoped("m0", "acc1", "fan-platform")).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> useCase.execute(cmd("m0", "k1")))
                .isInstanceOf(MembershipNotRenewableException.class);

        verify(paymentGateway, never()).verify(any());
        verify(membershipRepository, never()).save(any());
        verify(eventPublisher, never()).publishActivated(anyString(), anyString(), anyString(),
                any(), anyInt(), any(), any(), any());
    }

    @Test
    @DisplayName("missing / foreign prior → 404 MembershipNotFound")
    void renewMissingPrior() {
        when(membershipRepository.findByIdScoped("mX", "acc1", "fan-platform")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(cmd("mX", "k1")))
                .isInstanceOf(MembershipNotFoundException.class);
    }

    @Test
    @DisplayName("PG decline → 422 PaymentDeclined, no row")
    void renewDeclined() {
        Membership p = prior("m0", NOW.minus(40, ChronoUnit.DAYS), NOW.minus(1, ChronoUnit.DAYS));
        when(membershipRepository.findByIdScoped("m0", "acc1", "fan-platform")).thenReturn(Optional.of(p));
        when(idempotencyKeyRepository.find("fan-platform", "acc1", "k2")).thenReturn(Optional.empty());
        when(paymentGateway.verify(argThat(r -> "tok_decline".equals(r.paymentReference()))))
                .thenReturn(PaymentAuthorization.declined());

        RenewCommand declineCmd = new RenewCommand(ACTOR, "m0", 1, "tok_decline", "k2");
        assertThatThrownBy(() -> useCase.execute(declineCmd))
                .isInstanceOf(PaymentDeclinedException.class);

        verify(membershipRepository, never()).save(any());
    }
}
