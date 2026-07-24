package com.example.fanplatform.membership.infrastructure.payment;

import com.example.fanplatform.membership.domain.payment.PaymentGatewayPort;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Real PG adapter — PortOne V2 (ADR-001, architecture.md § PG Boundary).
 *
 * <p><b>Trust model — client-initiated payment + server-side verification.</b> The
 * browser SDK opens the payment window and returns a {@code paymentId} (passed here
 * as {@code paymentReference}); this adapter then calls the PortOne REST API to
 * <b>verify</b> that payment before the membership is created. The client's success
 * signal is NEVER trusted on its own — a forged or replayed {@code paymentId}, or a
 * payment for a smaller amount, is rejected here.
 *
 * <p>Verification passes only when the PortOne payment record reports
 * {@code status == PAID} AND its paid amount equals the {@code amountMinor} we are
 * charging (tamper guard) AND the currency is KRW. Any other outcome — a non-PAID
 * status, an amount mismatch, a missing/blank paymentId, a PortOne 4xx/5xx, a
 * network error, or an unparsable body — is <b>fail-closed to declined</b> (→ 422
 * PAYMENT_DECLINED, no membership row).
 *
 * <p>Active only under {@code @Profile("portone")} with an injected API secret; in
 * every other environment {@link MockPaymentGatewayAdapter} is the port, so CI /
 * tests / keyless local runs never reach real PortOne.
 */
@Component
@Profile("portone")
public class PortOnePaymentAdapter implements PaymentGatewayPort {

    private static final Logger log = LoggerFactory.getLogger(PortOnePaymentAdapter.class);

    private static final String EXPECTED_STATUS = "PAID";
    private static final int CONNECT_TIMEOUT_MS = 3_000;
    private static final int READ_TIMEOUT_MS = 5_000;

    private final RestClient restClient;

    public PortOnePaymentAdapter(
            @Value("${fan.payment.portone.api-base:https://api.portone.io}") String apiBase,
            @Value("${fan.payment.portone.api-secret}") String apiSecret,
            RestClient.Builder builder) {
        // Bounded timeouts — a slow/unreachable PortOne must fail-closed within a
        // few seconds, never hang the subscribe/renew transaction.
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
    public PaymentResult authorize(long amountMinor, int planMonths, String paymentReference, String idempotencyKey) {
        if (paymentReference == null || paymentReference.isBlank()) {
            log.warn("PortOne verify: blank paymentId -> declined");
            return PaymentResult.declined();
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
            return PaymentResult.declined();
        }
        if (payment == null) {
            log.warn("PortOne verify: empty body for paymentId={} -> declined", paymentReference);
            return PaymentResult.declined();
        }

        String status = payment.path("status").asText("");
        if (!EXPECTED_STATUS.equals(status)) {
            log.warn("PortOne verify: paymentId={} status={} (expected PAID) -> declined",
                    paymentReference, status);
            return PaymentResult.declined();
        }

        // PortOne V2 exposes the paid amount under `amount.total`; currency is the
        // top-level enum ("KRW" or "CURRENCY_KRW" across API versions).
        long paidTotal = payment.path("amount").path("total").asLong(-1L);
        if (paidTotal != amountMinor) {
            log.warn("PortOne verify: paymentId={} paidTotal={} != charged={} (amount tamper) -> declined",
                    paymentReference, paidTotal, amountMinor);
            return PaymentResult.declined();
        }

        String currency = payment.path("currency").asText("");
        if (!currency.endsWith("KRW")) {
            log.warn("PortOne verify: paymentId={} currency={} (expected KRW) -> declined",
                    paymentReference, currency);
            return PaymentResult.declined();
        }

        // Verified — the paymentId itself is the durable PG reference stored on the
        // Membership (mirrors how the mock stores pgmock_<uuid>).
        return PaymentResult.approved(paymentReference);
    }
}
