package com.example.libs.payment.toss;

import com.example.libs.payment.PaymentAuthorization;
import com.example.libs.payment.PaymentGatewayPort;
import com.example.libs.payment.PaymentGatewayStatus;
import com.example.libs.payment.PaymentStatusReadPort;
import com.example.libs.payment.PaymentVerificationRequest;
import com.example.libs.payment.PgConfirmFailedException;
import com.example.libs.payment.PgGatewayUnavailableException;
import com.example.libs.payment.RefundablePaymentGateway;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * Toss Payments adapter — confirm-model PG (project-agnostic; relocated from payment-service by
 * ADR-MONO-056 Phase 1), behind the canonical {@link PaymentGatewayPort}.
 *
 * <h2>{@link #verify} is a money-CAPTURE, not a read</h2>
 *
 * <p><b>IMPORTANT.</b> Toss is a <b>confirm-model</b> vendor: the port's {@link #verify}
 * performs {@code POST /v1/payments/confirm}, which is what <b>actually captures the money</b>
 * at the PG. It is not a read-back verification (unlike the verify-model PortOne adapter). A
 * consumer must never treat {@code verify} on this adapter as idempotent or side-effect-free —
 * calling it captures. On success it returns an approved {@link PaymentAuthorization} carrying
 * the captured payment's method + receipt URL.
 *
 * <h2>Resilience (ADR-MONO-005 Category B, TASK-BE-139)</h2>
 *
 * <p>{@code @CircuitBreaker(name="toss-payments")} +
 * {@code @Retry(name="toss-payments")} +
 * {@code @Bulkhead(name="toss-payments")} wrap every PG call. Configuration
 * lives in the consuming app's {@code application.yml} under
 * {@code resilience4j.*.instances.toss-payments}.
 *
 * <h2>Exception classification</h2>
 *
 * <ul>
 *   <li>4xx ({@link HttpClientErrorException}) — PG-side <b>definitive
 *       rejection</b>. Translated to {@link PgConfirmFailedException} and
 *       listed in R4j {@code ignore-exceptions} so retry / fallback do not
 *       fire. Caller sees a permanent-failure signal.</li>
 *   <li>5xx / network / timeout — <b>transport failure</b>. Bubbles up as
 *       {@code HttpServerErrorException} / {@code ResourceAccessException}
 *       which R4j {@code retry-exceptions} picks up; on retry exhaustion
 *       the fallback method runs.</li>
 *   <li>Fallback — translates any cause (retry exhaustion,
 *       {@link CallNotPermittedException} circuit open, {@link BulkheadFullException})
 *       to {@link PgGatewayUnavailableException}.</li>
 * </ul>
 *
 * <p>Caller policy: on {@link PgGatewayUnavailableException} the caller MUST keep the payment
 * row in its prior state (PG actual state unknown, idempotent retry expected); the
 * {@link PgConfirmFailedException} branch is the definitive-failure path.
 *
 * <p>Profile-agnostic by design: it is a plain {@link Component}. Selecting it is the consuming
 * application's concern — the lib bean does not pin itself to a profile.
 */
@Slf4j
@Component
@EnableConfigurationProperties(TossPaymentsProperties.class)
public class TossPaymentsAdapter
        implements PaymentGatewayPort, RefundablePaymentGateway, PaymentStatusReadPort {

    static final String CIRCUIT_NAME = "toss-payments";
    static final String CONFIRM_PATH = "/v1/payments/confirm";
    static final String CANCEL_PATH = "/v1/payments/{paymentKey}/cancel";
    static final String STATUS_PATH = "/v1/payments/{paymentKey}";

    private final RestClient restClient;

    public TossPaymentsAdapter(TossPaymentsProperties properties, RestClient.Builder restClientBuilder) {
        String encodedKey = Base64.getEncoder()
                .encodeToString((properties.secretKey() + ":").getBytes(StandardCharsets.UTF_8));

        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(properties.connectTimeoutMs()))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMs()));

        this.restClient = restClientBuilder
                .requestFactory(requestFactory)
                .baseUrl(properties.baseUrl())
                .defaultHeader("Authorization", "Basic " + encodedKey)
                .build();
    }

    /**
     * Verify (= <b>capture</b>) the payment via Toss {@code POST /v1/payments/confirm}. This is
     * the money-capture point — see the class-level note. Maps
     * {@link PaymentVerificationRequest#paymentReference()} → Toss {@code paymentKey},
     * {@link PaymentVerificationRequest#orderReference()} → {@code orderId},
     * {@link PaymentVerificationRequest#expectedAmountMinor()} → {@code amount}.
     */
    @Override
    @CircuitBreaker(name = CIRCUIT_NAME, fallbackMethod = "verifyFallback")
    @Retry(name = CIRCUIT_NAME)
    @Bulkhead(name = CIRCUIT_NAME)
    public PaymentAuthorization verify(PaymentVerificationRequest request) {
        String paymentKey = request.paymentReference();
        String orderId = request.orderReference();
        long amount = request.expectedAmountMinor();
        log.info("Toss Payments confirm request: paymentKey={}, orderId={}, amount={}", paymentKey, orderId, amount);

        Map<String, Object> body = Map.of(
                "paymentKey", paymentKey,
                "orderId", orderId,
                "amount", amount
        );

        try {
            JsonNode response = restClient.post()
                    .uri(CONFIRM_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            String paymentMethod = response != null && response.has("method")
                    ? response.get("method").asText() : null;
            String receiptUrl = response != null && response.has("receipt") && response.get("receipt").has("url")
                    ? response.get("receipt").get("url").asText() : null;

            log.info("Toss Payments confirm success: paymentKey={}, method={}", paymentKey, paymentMethod);
            return PaymentAuthorization.approved(paymentKey, paymentMethod, receiptUrl);
        } catch (HttpClientErrorException e) {
            // 4xx — PG definitively rejected our request. Permanent, no retry.
            log.error("Toss Payments confirm 4xx: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new PgConfirmFailedException("status=" + e.getStatusCode() + ", body=" + e.getResponseBodyAsString(), e);
        }
        // 5xx / IO / timeout propagate uncaught → R4j retry → fallback on exhaustion.
    }

    /**
     * R4j fallback for {@link #verify}. Invoked on retry exhaustion
     * ({@code HttpServerErrorException} / {@code ResourceAccessException}),
     * {@link CallNotPermittedException} (circuit OPEN), or
     * {@link BulkheadFullException} (bulkhead saturated). All causes are
     * translated to {@link PgGatewayUnavailableException}.
     *
     * <p>Must be {@code public} for R4j AOP to invoke it; signature must
     * match the annotated method plus a trailing {@link Throwable}.
     */
    @SuppressWarnings("unused")
    public PaymentAuthorization verifyFallback(PaymentVerificationRequest request, Throwable cause) {
        // PgConfirmFailedException is in R4j ignore-exceptions so it never
        // reaches the fallback. Defensive re-throw for the contract.
        if (cause instanceof PgConfirmFailedException pce) {
            throw pce;
        }
        String paymentKey = request.paymentReference();
        log.warn("Toss Payments confirm fallback: paymentKey={}, cause={}({})",
                paymentKey, cause.getClass().getSimpleName(), cause.getMessage());
        throw new PgGatewayUnavailableException(
                "confirm exhausted for paymentKey=" + paymentKey
                        + " (" + cause.getClass().getSimpleName() + ")", cause);
    }

    /** Full reversal — Toss {@code POST /v1/payments/{paymentKey}/cancel}. */
    @Override
    @CircuitBreaker(name = CIRCUIT_NAME, fallbackMethod = "refundFallback")
    @Retry(name = CIRCUIT_NAME)
    @Bulkhead(name = CIRCUIT_NAME)
    public void refund(String vendorPaymentRef, String reason) {
        log.info("Toss Payments cancel request: paymentKey={}, reason={}", vendorPaymentRef, reason);

        Map<String, String> body = Map.of("cancelReason", reason);

        try {
            restClient.post()
                    .uri(CANCEL_PATH, vendorPaymentRef)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Toss Payments cancel success: paymentKey={}", vendorPaymentRef);
        } catch (HttpClientErrorException e) {
            // 4xx — PG definitively rejected the cancel. Permanent, no retry.
            log.error("Toss Payments cancel 4xx: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new PgConfirmFailedException("Cancel failed: status=" + e.getStatusCode() + ", body=" + e.getResponseBodyAsString(), e);
        }
        // 5xx / IO / timeout propagate uncaught → R4j retry → fallback on exhaustion.
    }

    /**
     * R4j fallback for {@link #refund(String, String)}. See {@link #verifyFallback}
     * for the classification contract.
     */
    @SuppressWarnings("unused")
    public void refundFallback(String vendorPaymentRef, String reason, Throwable cause) {
        if (cause instanceof PgConfirmFailedException pce) {
            throw pce;
        }
        log.warn("Toss Payments cancel fallback: paymentKey={}, cause={}({})",
                vendorPaymentRef, cause.getClass().getSimpleName(), cause.getMessage());
        throw new PgGatewayUnavailableException(
                "cancel exhausted for paymentKey=" + vendorPaymentRef
                        + " (" + cause.getClass().getSimpleName() + ")", cause);
    }

    /**
     * Partial (or full) reversal of {@code amountMinor} minor units — Toss {@code cancelAmount}.
     */
    @Override
    @CircuitBreaker(name = CIRCUIT_NAME, fallbackMethod = "refundAmountFallback")
    @Retry(name = CIRCUIT_NAME)
    @Bulkhead(name = CIRCUIT_NAME)
    public void refund(String vendorPaymentRef, String reason, long amountMinor) {
        log.info("Toss Payments partial cancel request: paymentKey={}, reason={}, cancelAmount={}",
                vendorPaymentRef, reason, amountMinor);

        Map<String, Object> body = Map.of("cancelReason", reason, "cancelAmount", amountMinor);

        try {
            restClient.post()
                    .uri(CANCEL_PATH, vendorPaymentRef)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Toss Payments partial cancel success: paymentKey={}, cancelAmount={}", vendorPaymentRef, amountMinor);
        } catch (HttpClientErrorException e) {
            log.error("Toss Payments partial cancel 4xx: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new PgConfirmFailedException("Cancel failed: status=" + e.getStatusCode() + ", body=" + e.getResponseBodyAsString(), e);
        }
    }

    /** R4j fallback for the partial {@link #refund(String, String, long)}. */
    @SuppressWarnings("unused")
    public void refundAmountFallback(String vendorPaymentRef, String reason, long amountMinor, Throwable cause) {
        if (cause instanceof PgConfirmFailedException pce) {
            throw pce;
        }
        log.warn("Toss Payments partial cancel fallback: paymentKey={}, cancelAmount={}, cause={}({})",
                vendorPaymentRef, amountMinor, cause.getClass().getSimpleName(), cause.getMessage());
        throw new PgGatewayUnavailableException(
                "partial cancel exhausted for paymentKey=" + vendorPaymentRef
                        + " (" + cause.getClass().getSimpleName() + ")", cause);
    }

    /**
     * Read-only PG state lookup for the stranded-refund double-refund guard (TASK-BE-438).
     * Maps the Toss {@code status} field down to {@link PaymentGatewayStatus}:
     * {@code CANCELED → CANCELED} (already reversed); {@code DONE} / {@code PARTIAL_CANCELED}
     * → {@code CAPTURED} (still held, needs a cancel); anything else (incl. a missing/unparseable
     * status) → {@code UNKNOWN}. A 4xx is a definitive read error; both 4xx and transport failure
     * are surfaced to the caller, which treats them as transient (never infers resolution from a
     * read error). Wrapped with the same R4j instance as verify/refund (TASK-BE-139).
     */
    @Override
    @CircuitBreaker(name = CIRCUIT_NAME, fallbackMethod = "fetchStatusFallback")
    @Retry(name = CIRCUIT_NAME)
    @Bulkhead(name = CIRCUIT_NAME)
    public PaymentGatewayStatus fetchStatus(String vendorPaymentRef) {
        log.info("Toss Payments status request: paymentKey={}", vendorPaymentRef);
        try {
            JsonNode response = restClient.get()
                    .uri(STATUS_PATH, vendorPaymentRef)
                    .retrieve()
                    .body(JsonNode.class);

            String status = response != null && response.has("status")
                    ? response.get("status").asText() : null;
            PaymentGatewayStatus mapped = mapStatus(status);
            log.info("Toss Payments status: paymentKey={}, raw={}, mapped={}", vendorPaymentRef, status, mapped);
            return mapped;
        } catch (HttpClientErrorException e) {
            // 4xx — definitive read error (e.g. not found). Never infer CANCELED from it.
            log.error("Toss Payments status 4xx: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new PgConfirmFailedException("fetchStatus status=" + e.getStatusCode()
                    + ", body=" + e.getResponseBodyAsString(), e);
        }
        // 5xx / IO / timeout propagate uncaught → R4j retry → fallback on exhaustion.
    }

    private static PaymentGatewayStatus mapStatus(String tossStatus) {
        if (tossStatus == null) {
            return PaymentGatewayStatus.UNKNOWN;
        }
        return switch (tossStatus) {
            case "CANCELED" -> PaymentGatewayStatus.CANCELED;
            case "DONE", "PARTIAL_CANCELED" -> PaymentGatewayStatus.CAPTURED;
            default -> PaymentGatewayStatus.UNKNOWN;
        };
    }

    /** R4j fallback for {@link #fetchStatus}. See {@link #verifyFallback} for the classification contract. */
    @SuppressWarnings("unused")
    public PaymentGatewayStatus fetchStatusFallback(String vendorPaymentRef, Throwable cause) {
        if (cause instanceof PgConfirmFailedException pce) {
            throw pce;
        }
        log.warn("Toss Payments status fallback: paymentKey={}, cause={}({})",
                vendorPaymentRef, cause.getClass().getSimpleName(), cause.getMessage());
        throw new PgGatewayUnavailableException(
                "fetchStatus exhausted for paymentKey=" + vendorPaymentRef
                        + " (" + cause.getClass().getSimpleName() + ")", cause);
    }
}
