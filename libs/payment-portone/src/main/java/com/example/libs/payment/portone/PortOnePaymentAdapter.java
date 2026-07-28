package com.example.libs.payment.portone;

import com.example.libs.payment.PaymentAuthorization;
import com.example.libs.payment.PaymentGatewayPort;
import com.example.libs.payment.PaymentVerificationRequest;
import com.example.libs.payment.PgGatewayUnavailableException;
import com.example.libs.payment.RecurringBillingGateway;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

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
 * <p><b>Also implements {@link RecurringBillingGateway}</b> (ADR-MONO-057) — a server-initiated
 * billing-key charge, sharing this adapter's already-wired {@link RestClient} (same base URL, same
 * {@code Authorization: PortOne <secret>} header) rather than duplicating the wiring in a separate
 * class. That op is a money-<b>write</b> and therefore follows a stricter failure contract than
 * {@link #verify} — see {@link #chargeBillingKey}.
 *
 * <p>Profile-agnostic by design: it is a plain {@link Component}. Selecting it (vs a mock) is
 * the consuming application's concern — the lib bean does not pin itself to a profile.
 */
@Component
public class PortOnePaymentAdapter implements PaymentGatewayPort, RecurringBillingGateway {

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

    /**
     * Server-initiated billing-key charge (ADR-MONO-057) — PortOne V2
     * {@code POST /payments/{paymentId}/billing-key} with a JSON body carrying
     * {@code billingKey}, {@code orderName}, {@code amount: { total }}, {@code currency}. The
     * response is parsed the same way {@link #verify} parses a payment object ({@code status},
     * {@code amount.total}, {@code currency}) to build the {@link PaymentAuthorization}.
     *
     * <p><b>FAILURE CONTRACT (this is a money-WRITE — stricter than {@link #verify}).</b> Because a
     * charge moves money, a lost/errored response is ambiguous (the charge may have captured even
     * though we never got a definitive answer). This method therefore does NOT blanket
     * fail-close-to-declined the way {@link #verify} does; it distinguishes (per
     * {@link RecurringBillingGateway}'s type-level contract):
     * <ul>
     *   <li><b>Definitive rejection → {@link PaymentAuthorization#declined()}</b>: a blank
     *       billingKey/paymentId (no call is made, so no money moved); a PortOne <b>4xx</b>
     *       ({@link HttpClientErrorException}, e.g. an invalid/revoked billing key or insufficient
     *       funds); or a parseable response whose {@code status} is a terminal non-success
     *       ({@code FAILED}). In every one of these the PG has told us unambiguously that no charge
     *       stands.</li>
     *   <li><b>Ambiguous → throw {@link PgGatewayUnavailableException}</b>: a 5xx, a
     *       timeout/connection error, an empty/unparseable body, or a 2xx with a non-terminal /
     *       unrecognised {@code status} (e.g. {@code READY}, {@code PAY_PENDING}), or a
     *       {@code PAID} whose amount/currency does not match what we charged (a money anomaly). We
     *       cannot prove money did or did not move, so we surface the ambiguity — the caller
     *       reconciles via {@link #verify}({@code paymentId}, ...) and MUST NOT blindly retry.</li>
     * </ul>
     * A successful ({@code PAID}, matching amount + currency) charge returns
     * {@link PaymentAuthorization#approved}; PortOne is verify-model, so no paymentMethod/receiptUrl
     * is filled (mirrors {@link #verify}).
     *
     * <p><b>NOT live-verified.</b> Unlike {@link #verify} (already live-proven against a real
     * PortOne test account), this billing-key charge path is a best-effort implementation against
     * PortOne's documented V2 convention, unit-tested via MockWebServer with an assumed request
     * (path {@code /payments/{paymentId}/billing-key}, body {@code billingKey}/{@code orderName}/
     * {@code amount.total}/{@code currency}) and response ({@code status}/{@code amount.total}/
     * {@code currency}) shape. The exact endpoint, field names, and status enum MUST be
     * reconfirmed against PortOne's current V2 API reference before this is wired to a live
     * consumer (ADR-MONO-057 itself flags the wire format as implementation-time-verified).
     */
    @Override
    public PaymentAuthorization chargeBillingKey(
            String billingKey, String paymentId, long amountMinor, String currency, String orderName) {
        if (billingKey == null || billingKey.isBlank() || paymentId == null || paymentId.isBlank()) {
            // No call is made → no money can have moved → safe to decline (definitive).
            log.warn("PortOne charge: blank billingKey/paymentId -> declined (no call)");
            return PaymentAuthorization.declined();
        }

        Map<String, Object> body = Map.of(
                "billingKey", billingKey,
                "orderName", orderName,
                "amount", Map.of("total", amountMinor),
                "currency", currency);

        final JsonNode payment;
        try {
            payment = restClient.post()
                    .uri("/payments/{paymentId}/billing-key", paymentId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException e) {
            // 4xx — PG definitively rejected the charge (invalid/revoked key, insufficient funds).
            // Unambiguous "no money moved" → declined. Never log the API secret.
            log.warn("PortOne charge: 4xx for paymentId={} status={} -> declined",
                    paymentId, e.getStatusCode());
            return PaymentAuthorization.declined();
        } catch (RestClientException e) {
            // 5xx / timeout / connection error — the PG-side outcome is UNKNOWN. Money may have
            // moved. Surface the ambiguity so the caller reconciles via verify(paymentId, ...).
            log.warn("PortOne charge: ambiguous transport failure for paymentId={} ({}) -> unavailable",
                    paymentId, e.getClass().getSimpleName());
            throw new PgGatewayUnavailableException(
                    "billing-key charge outcome unknown for paymentId=" + paymentId
                            + " (" + e.getClass().getSimpleName() + ")", e);
        }

        if (payment == null) {
            // 2xx but no body — we cannot tell whether the charge captured. Ambiguous.
            log.warn("PortOne charge: empty body for paymentId={} -> unavailable (ambiguous)", paymentId);
            throw new PgGatewayUnavailableException("billing-key charge returned empty body for paymentId=" + paymentId);
        }

        String status = payment.path("status").asText("");
        if (EXPECTED_STATUS.equals(status)) {
            // PAID — confirm the captured amount + currency match what we asked to charge. A
            // successful-status charge for a DIFFERENT amount/currency is a money anomaly we must
            // NOT surface as a clean approval, and NOT as declined (money moved) → reconcile.
            long paidTotal = payment.path("amount").path("total").asLong(-1L);
            String paidCurrency = payment.path("currency").asText("");
            if (paidTotal != amountMinor || !paidCurrency.endsWith(currency)) {
                log.warn("PortOne charge: paymentId={} PAID but paidTotal={}/currency={} != charged={}/{}"
                                + " (anomaly) -> unavailable",
                        paymentId, paidTotal, paidCurrency, amountMinor, currency);
                throw new PgGatewayUnavailableException(
                        "billing-key charge PAID with mismatched amount/currency for paymentId=" + paymentId);
            }
            // Captured. The paymentId is the durable PG reference; verify-model → no method/receipt.
            return PaymentAuthorization.approved(paymentId, null, null);
        }

        if ("FAILED".equals(status)) {
            // Terminal non-success delivered in a 2xx body — unambiguous "no charge stands".
            log.warn("PortOne charge: paymentId={} status=FAILED -> declined", paymentId);
            return PaymentAuthorization.declined();
        }

        // Any other status (READY, PAY_PENDING, missing, unrecognised) is non-terminal — we cannot
        // prove the charge captured or definitively failed. Ambiguous → reconcile via verify.
        log.warn("PortOne charge: paymentId={} non-terminal status={} -> unavailable (ambiguous)",
                paymentId, status);
        throw new PgGatewayUnavailableException(
                "billing-key charge non-terminal status=" + status + " for paymentId=" + paymentId);
    }
}
