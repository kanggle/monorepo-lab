package com.example.payment.adapter.out.pg;

import com.example.payment.application.exception.PgConfirmFailedException;
import com.example.payment.application.exception.PgGatewayUnavailableException;
import com.example.payment.application.port.out.PaymentGatewayConfirmResult;
import com.example.payment.application.port.out.PaymentGatewayPort;
import com.example.payment.application.port.out.PaymentGatewayStatus;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
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
 * Toss Payments adapter — synchronous PG confirm / cancel.
 *
 * <h2>Resilience (ADR-MONO-005 Category B, TASK-BE-139)</h2>
 *
 * <p>{@code @CircuitBreaker(name="toss-payments")} +
 * {@code @Retry(name="toss-payments")} +
 * {@code @Bulkhead(name="toss-payments")} wrap both PG calls. Configuration
 * lives in {@code application.yml} under {@code resilience4j.*.instances.toss-payments}.
 *
 * <h2>Exception classification</h2>
 *
 * <ul>
 *   <li>4xx ({@link HttpClientErrorException}) — PG-side <b>definitive
 *       rejection</b>. Translated to {@link PgConfirmFailedException} and
 *       listed in R4j {@code ignore-exceptions} so retry / fallback do not
 *       fire. Caller sees {@code PG_CONFIRM_FAILED} (502).</li>
 *   <li>5xx / network / timeout — <b>transport failure</b>. Bubbles up as
 *       {@code HttpServerErrorException} / {@code ResourceAccessException}
 *       which R4j {@code retry-exceptions} picks up; on retry exhaustion
 *       the fallback method runs.</li>
 *   <li>Fallback ({@link #confirmFallback}, {@link #cancelFallback}) —
 *       translates any cause (retry exhaustion, {@link CallNotPermittedException}
 *       circuit open, {@link BulkheadFullException}) to
 *       {@link PgGatewayUnavailableException}. Caller sees
 *       {@code PG_GATEWAY_UNAVAILABLE} (503).</li>
 * </ul>
 *
 * <p>Caller policy: {@code PaymentConfirmService} / {@code PaymentRefundService}
 * MUST keep the payment row in its prior state on {@link PgGatewayUnavailableException}
 * (PG actual state unknown, idempotent retry expected). The existing
 * {@link PgConfirmFailedException} branch continues to transition the row
 * to {@code FAILED} unchanged.
 */
@Slf4j
@Component
@Profile("!standalone")
@EnableConfigurationProperties(TossPaymentsProperties.class)
public class TossPaymentsAdapter implements PaymentGatewayPort {

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

    @Override
    @CircuitBreaker(name = CIRCUIT_NAME, fallbackMethod = "confirmFallback")
    @Retry(name = CIRCUIT_NAME)
    @Bulkhead(name = CIRCUIT_NAME)
    public PaymentGatewayConfirmResult confirmPayment(String paymentKey, String orderId, long amount) {
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
            return new PaymentGatewayConfirmResult(paymentMethod, receiptUrl);
        } catch (HttpClientErrorException e) {
            // 4xx — PG definitively rejected our request. Permanent, no retry.
            log.error("Toss Payments confirm 4xx: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new PgConfirmFailedException("status=" + e.getStatusCode() + ", body=" + e.getResponseBodyAsString(), e);
        }
        // 5xx / IO / timeout propagate uncaught → R4j retry → fallback on exhaustion.
    }

    /**
     * R4j fallback for {@link #confirmPayment}. Invoked on retry exhaustion
     * ({@code HttpServerErrorException} / {@code ResourceAccessException}),
     * {@link CallNotPermittedException} (circuit OPEN), or
     * {@link BulkheadFullException} (bulkhead saturated). All causes are
     * translated to {@link PgGatewayUnavailableException}.
     *
     * <p>Must be {@code public} for R4j AOP to invoke it; signature must
     * match the annotated method plus a trailing {@link Throwable}.
     */
    @SuppressWarnings("unused")
    public PaymentGatewayConfirmResult confirmFallback(String paymentKey, String orderId, long amount, Throwable cause) {
        // PgConfirmFailedException is in R4j ignore-exceptions so it never
        // reaches the fallback. Defensive re-throw for the contract.
        if (cause instanceof PgConfirmFailedException pce) {
            throw pce;
        }
        log.warn("Toss Payments confirm fallback: paymentKey={}, cause={}({})",
                paymentKey, cause.getClass().getSimpleName(), cause.getMessage());
        throw new PgGatewayUnavailableException(
                "confirm exhausted for paymentKey=" + paymentKey
                        + " (" + cause.getClass().getSimpleName() + ")", cause);
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_NAME, fallbackMethod = "cancelFallback")
    @Retry(name = CIRCUIT_NAME)
    @Bulkhead(name = CIRCUIT_NAME)
    public void cancelPayment(String paymentKey, String cancelReason) {
        log.info("Toss Payments cancel request: paymentKey={}, reason={}", paymentKey, cancelReason);

        Map<String, String> body = Map.of("cancelReason", cancelReason);

        try {
            restClient.post()
                    .uri(CANCEL_PATH, paymentKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Toss Payments cancel success: paymentKey={}", paymentKey);
        } catch (HttpClientErrorException e) {
            // 4xx — PG definitively rejected the cancel. Permanent, no retry.
            log.error("Toss Payments cancel 4xx: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new PgConfirmFailedException("Cancel failed: status=" + e.getStatusCode() + ", body=" + e.getResponseBodyAsString(), e);
        }
        // 5xx / IO / timeout propagate uncaught → R4j retry → fallback on exhaustion.
    }

    /**
     * R4j fallback for {@link #cancelPayment}. See {@link #confirmFallback}
     * for the classification contract.
     */
    @SuppressWarnings("unused")
    public void cancelFallback(String paymentKey, String cancelReason, Throwable cause) {
        if (cause instanceof PgConfirmFailedException pce) {
            throw pce;
        }
        log.warn("Toss Payments cancel fallback: paymentKey={}, cause={}({})",
                paymentKey, cause.getClass().getSimpleName(), cause.getMessage());
        throw new PgGatewayUnavailableException(
                "cancel exhausted for paymentKey=" + paymentKey
                        + " (" + cause.getClass().getSimpleName() + ")", cause);
    }

    @Override
    @CircuitBreaker(name = CIRCUIT_NAME, fallbackMethod = "cancelAmountFallback")
    @Retry(name = CIRCUIT_NAME)
    @Bulkhead(name = CIRCUIT_NAME)
    public void cancelPayment(String paymentKey, String cancelReason, long cancelAmount) {
        log.info("Toss Payments partial cancel request: paymentKey={}, reason={}, cancelAmount={}",
                paymentKey, cancelReason, cancelAmount);

        Map<String, Object> body = Map.of("cancelReason", cancelReason, "cancelAmount", cancelAmount);

        try {
            restClient.post()
                    .uri(CANCEL_PATH, paymentKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Toss Payments partial cancel success: paymentKey={}, cancelAmount={}", paymentKey, cancelAmount);
        } catch (HttpClientErrorException e) {
            log.error("Toss Payments partial cancel 4xx: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new PgConfirmFailedException("Cancel failed: status=" + e.getStatusCode() + ", body=" + e.getResponseBodyAsString(), e);
        }
    }

    /** R4j fallback for the partial {@link #cancelPayment(String, String, long)}. */
    @SuppressWarnings("unused")
    public void cancelAmountFallback(String paymentKey, String cancelReason, long cancelAmount, Throwable cause) {
        if (cause instanceof PgConfirmFailedException pce) {
            throw pce;
        }
        log.warn("Toss Payments partial cancel fallback: paymentKey={}, cancelAmount={}, cause={}({})",
                paymentKey, cancelAmount, cause.getClass().getSimpleName(), cause.getMessage());
        throw new PgGatewayUnavailableException(
                "partial cancel exhausted for paymentKey=" + paymentKey
                        + " (" + cause.getClass().getSimpleName() + ")", cause);
    }

    /**
     * Read-only PG state lookup for the stranded-refund double-refund guard (TASK-BE-438).
     * Maps the Toss {@code status} field down to {@link PaymentGatewayStatus}:
     * {@code CANCELED → CANCELED} (already reversed); {@code DONE} / {@code PARTIAL_CANCELED}
     * → {@code CAPTURED} (still held, needs a cancel); anything else (incl. a missing/unparseable
     * status) → {@code UNKNOWN}. A 4xx is a definitive read error; both 4xx and transport failure
     * are surfaced to the caller, which treats them as transient (never infers resolution from a
     * read error). Wrapped with the same R4j instance as confirm/cancel (TASK-BE-139).
     */
    @Override
    @CircuitBreaker(name = CIRCUIT_NAME, fallbackMethod = "fetchStatusFallback")
    @Retry(name = CIRCUIT_NAME)
    @Bulkhead(name = CIRCUIT_NAME)
    public PaymentGatewayStatus fetchStatus(String paymentKey) {
        log.info("Toss Payments status request: paymentKey={}", paymentKey);
        try {
            JsonNode response = restClient.get()
                    .uri(STATUS_PATH, paymentKey)
                    .retrieve()
                    .body(JsonNode.class);

            String status = response != null && response.has("status")
                    ? response.get("status").asText() : null;
            PaymentGatewayStatus mapped = mapStatus(status);
            log.info("Toss Payments status: paymentKey={}, raw={}, mapped={}", paymentKey, status, mapped);
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

    /** R4j fallback for {@link #fetchStatus}. See {@link #confirmFallback} for the classification contract. */
    @SuppressWarnings("unused")
    public PaymentGatewayStatus fetchStatusFallback(String paymentKey, Throwable cause) {
        if (cause instanceof PgConfirmFailedException pce) {
            throw pce;
        }
        log.warn("Toss Payments status fallback: paymentKey={}, cause={}({})",
                paymentKey, cause.getClass().getSimpleName(), cause.getMessage());
        throw new PgGatewayUnavailableException(
                "fetchStatus exhausted for paymentKey=" + paymentKey
                        + " (" + cause.getClass().getSimpleName() + ")", cause);
    }
}
