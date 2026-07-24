package com.example.libs.payment.portone;

import com.example.libs.payment.PaymentAuthorization;
import com.example.libs.payment.PaymentGatewayPort;
import com.example.libs.payment.PaymentVerificationRequest;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Real PG adapter — PortOne V2 (verify-model), project-agnostic (ADR-MONO-056 Phase 1;
 * relocated verbatim-in-behavior from fan-platform membership-service, fan ADR-001).
 *
 * <p><b>Trust model — client-initiated payment + server-side verification.</b> The browser SDK
 * opens the payment window and returns a {@code paymentId} (carried here as
 * {@link PaymentVerificationRequest#paymentReference()}); this adapter then calls the PortOne
 * REST API to <b>verify</b> that payment. The client's success signal is NEVER trusted on its
 * own — a forged or replayed {@code paymentId}, or a payment for a smaller amount, is rejected
 * here.
 *
 * <p>Verification passes only when the PortOne payment record reports {@code status == PAID} AND
 * its paid amount equals {@link PaymentVerificationRequest#expectedAmountMinor()} (tamper guard)
 * AND the currency is KRW. Any other outcome — a non-PAID status, an amount mismatch, a
 * missing/blank paymentId, a PortOne 4xx/5xx, a network error, or an unparsable body — is
 * <b>fail-closed to declined</b> ({@link PaymentAuthorization#declined()}); this adapter NEVER
 * throws for a failed verification.
 *
 * <p>Profile-agnostic by design: it is a plain {@link Component}. Selecting it (vs a mock) is
 * the consuming application's concern — the lib bean does not pin itself to a profile.
 */
@Component
public class PortOnePaymentAdapter implements PaymentGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(PortOnePaymentAdapter.class);

    private static final String EXPECTED_STATUS = "PAID";
    private static final int CONNECT_TIMEOUT_MS = 3_000;
    private static final int READ_TIMEOUT_MS = 5_000;

    private final RestClient restClient;

    public PortOnePaymentAdapter(
            @Value("${payment.portone.api-base:https://api.portone.io}") String apiBase,
            @Value("${payment.portone.api-secret}") String apiSecret,
            RestClient.Builder builder) {
        // Bounded timeouts — a slow/unreachable PortOne must fail-closed within a
        // few seconds, never hang the verify transaction.
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(CONNECT_TIMEOUT_MS);
        rf.setReadTimeout(READ_TIMEOUT_MS);
        this.restClient = builder
                .baseUrl(apiBase)
                .requestFactory(rf)
                // PortOne V2 REST auth: `Authorization: PortOne <API secret>`.
                .defaultHeader("Authorization", "PortOne " + apiSecret)
                .build();
    }

    @Override
    public PaymentAuthorization verify(PaymentVerificationRequest request) {
        String paymentReference = request.paymentReference();
        long expectedAmountMinor = request.expectedAmountMinor();

        if (paymentReference == null || paymentReference.isBlank()) {
            log.warn("PortOne verify: blank paymentId -> declined");
            return PaymentAuthorization.declined();
        }
        final JsonNode payment;
        try {
            payment = restClient.get()
                    .uri("/payments/{paymentId}", paymentReference)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception e) {
            // 4xx (e.g. 404 unknown paymentId), 5xx, timeouts, connection errors —
            // all fail-closed. Never log the API secret; RestClient does not expose it.
            log.warn("PortOne verify: lookup failed for paymentId={} ({}) -> declined",
                    paymentReference, e.getClass().getSimpleName());
            return PaymentAuthorization.declined();
        }
        if (payment == null) {
            log.warn("PortOne verify: empty body for paymentId={} -> declined", paymentReference);
            return PaymentAuthorization.declined();
        }

        String status = payment.path("status").asText("");
        if (!EXPECTED_STATUS.equals(status)) {
            log.warn("PortOne verify: paymentId={} status={} (expected PAID) -> declined",
                    paymentReference, status);
            return PaymentAuthorization.declined();
        }

        // PortOne V2 exposes the paid amount under `amount.total`; currency is the
        // top-level enum ("KRW" or "CURRENCY_KRW" across API versions).
        long paidTotal = payment.path("amount").path("total").asLong(-1L);
        if (paidTotal != expectedAmountMinor) {
            log.warn("PortOne verify: paymentId={} paidTotal={} != charged={} (amount tamper) -> declined",
                    paymentReference, paidTotal, expectedAmountMinor);
            return PaymentAuthorization.declined();
        }

        String currency = payment.path("currency").asText("");
        if (!currency.endsWith("KRW")) {
            log.warn("PortOne verify: paymentId={} currency={} (expected KRW) -> declined",
                    paymentReference, currency);
            return PaymentAuthorization.declined();
        }

        // Verified — the paymentId itself is the durable PG reference. PortOne is verify-model,
        // so it fills no paymentMethod/receiptUrl (those are confirm-model, Toss-only).
        return PaymentAuthorization.approved(paymentReference, null, null);
    }
}
