package com.example.fanplatform.membership.application.billing;

import com.example.fanplatform.membership.domain.membership.Membership;
import com.example.fanplatform.membership.domain.membership.MembershipRepository;
import com.example.fanplatform.membership.domain.membership.MembershipTier;
import com.example.libs.payment.PaymentAuthorization;
import com.example.libs.payment.PaymentGatewayPort;
import com.example.libs.payment.PaymentVerificationRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class WebhookReconcileUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-07-12T00:00:00Z");

    @Mock MembershipRepository membershipRepository;
    @Mock PaymentGatewayPort paymentGateway;
    @Captor ArgumentCaptor<PaymentVerificationRequest> verifyCaptor;

    private WebhookReconcileUseCase useCase() {
        return new WebhookReconcileUseCase(membershipRepository, paymentGateway);
    }

    @Test
    @DisplayName("blank paymentId → no-op, never touches the PG")
    void blankPaymentId() {
        useCase().reconcileByPaymentId("  ");
        verifyNoInteractions(membershipRepository, paymentGateway);
    }

    @Test
    @DisplayName("no membership carries the paymentId → ack, never trusts payload, no verify")
    void unknownPaymentIdIsAck() {
        when(membershipRepository.findByPaymentRef("pay-x")).thenReturn(Optional.empty());

        useCase().reconcileByPaymentId("pay-x");

        verify(paymentGateway, never()).verify(any());
    }

    @Test
    @DisplayName("membership already renewed for the paymentId → confirm via verify (idempotent no-op)")
    void alreadyRenewedConfirmsViaVerify() {
        Membership renewed = Membership.activate("m1", "fan-platform", "acc1", MembershipTier.PREMIUM,
                NOW, NOW.plus(30, ChronoUnit.DAYS), 1, "pay-1", NOW);
        when(membershipRepository.findByPaymentRef("pay-1")).thenReturn(Optional.of(renewed));
        when(paymentGateway.verify(any())).thenReturn(PaymentAuthorization.approved("pay-1", null, null));

        useCase().reconcileByPaymentId("pay-1");

        verify(paymentGateway).verify(verifyCaptor.capture());
        // Re-verifies the TRUTH by paymentId + the tier's expected amount — never the payload.
        assertThat(verifyCaptor.getValue().paymentReference()).isEqualTo("pay-1");
        assertThat(verifyCaptor.getValue().expectedAmountMinor()).isEqualTo(17_900L);
        assertThat(verifyCaptor.getValue().currency()).isEqualTo("KRW");
    }
}
