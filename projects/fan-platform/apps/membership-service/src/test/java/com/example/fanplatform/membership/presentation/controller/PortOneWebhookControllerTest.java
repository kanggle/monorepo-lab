package com.example.fanplatform.membership.presentation.controller;

import com.example.fanplatform.membership.application.billing.WebhookReconcileUseCase;
import com.example.libs.payment.portone.PortOneWebhookVerifier;
import com.example.libs.payment.portone.PortOneWebhookVerifier.VerifiedWebhookEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the webhook controller's trust gate: an invalid signature is
 * rejected 401 BEFORE any reconcile; a valid signature triggers a reconcile-by-
 * paymentId and returns 200. (The security bypass wiring — PublicPaths — and the
 * synthetic-signature path are proven by the Testcontainers webhook IT in CI.)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class PortOneWebhookControllerTest {

    private static final String SECRET = "whsec_test";

    @Mock PortOneWebhookVerifier verifier;
    @Mock WebhookReconcileUseCase reconcileUseCase;

    private PortOneWebhookController controller() {
        return new PortOneWebhookController(verifier, reconcileUseCase, SECRET);
    }

    @Test
    @DisplayName("invalid / missing signature → 401, reconcile never invoked")
    void invalidSignatureRejected() {
        when(verifier.verify(eq(SECRET), anyString(), any())).thenReturn(Optional.empty());

        ResponseEntity<Void> resp = controller().receive("{\"x\":1}", Map.of("webhook-signature", "bad"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(reconcileUseCase);
    }

    @Test
    @DisplayName("valid signature → reconcile-by-paymentId, 200")
    void validSignatureReconciles() {
        when(verifier.verify(eq(SECRET), anyString(), any()))
                .thenReturn(Optional.of(new VerifiedWebhookEvent("pay-1", "Transaction.Paid")));

        ResponseEntity<Void> resp = controller().receive("{\"data\":{\"paymentId\":\"pay-1\"}}",
                Map.of("webhook-signature", "v1,good"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(reconcileUseCase).reconcileByPaymentId("pay-1");
    }

    @Test
    @DisplayName("null body → verifier rejects → 401 (never NPEs)")
    void nullBodyRejected() {
        when(verifier.verify(eq(SECRET), any(), any())).thenReturn(Optional.empty());

        ResponseEntity<Void> resp = controller().receive(null, Map.of());

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(reconcileUseCase, never()).reconcileByPaymentId(anyString());
    }
}
