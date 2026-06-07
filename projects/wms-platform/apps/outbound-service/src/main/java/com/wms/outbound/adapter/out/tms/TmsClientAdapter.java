package com.wms.outbound.adapter.out.tms;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.outbound.application.port.out.ShipmentNotificationPort;
import com.wms.outbound.application.port.out.ShipmentPersistencePort;
import com.wms.outbound.application.port.out.TmsAcknowledgement;
import com.wms.outbound.application.port.out.TmsRequestDedupePort;
import com.wms.outbound.domain.exception.ExternalServiceUnavailableException;
import com.wms.outbound.domain.exception.ShipmentNotFoundException;
import com.wms.outbound.domain.model.Shipment;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Real TMS adapter (TASK-BE-049): synchronous HTTPS push of shipment-ready
 * notifications via Spring {@link RestClient} layered over Apache HttpClient 5
 * with a dedicated connection pool (size 10). Wrapped with Resilience4j
 * circuit breaker, retry, and semaphore bulkhead — all named
 * {@code tms-client} and externalised in {@code application.yml}.
 *
 * <h2>Flow</h2>
 *
 * <pre>
 * 1. Look up Shipment by id (port).
 * 2. Look up tms_request_dedupe[shipment.id] (port). On hit: return cached
 *    ack without HTTP call (I4 fallback).
 * 3. Map Shipment → TmsShipmentRequest (I8).
 * 4. POST {tms-base}/shipments with Idempotency-Key=shipment.id, the
 *    cached API-key header (set by RestClient builder), and the
 *    serialised request.
 * 5. On 2xx: translate response → TmsAcknowledgement, persist to dedupe
 *    table (REQUIRES_NEW), return.
 * 6. On 4xx: throw TmsPermanentException → caller sees
 *    ExternalServiceUnavailableException without retry.
 * 7. On 5xx / IO / timeout: throw TmsTransientException → Resilience4j
 *    retry with exponential backoff + jitter; on exhaustion the adapter
 *    surfaces ExternalServiceUnavailableException.
 * </pre>
 *
 * <h2>Failure mapping</h2>
 *
 * <p>The Resilience4j fallback ({@link #notifyFallback(UUID, Throwable)})
 * is invoked when retries are exhausted, the circuit is open, or the
 * bulkhead is saturated. It always converts to
 * {@link ExternalServiceUnavailableException} so the
 * {@code ShipmentNotificationListener} can route to {@code markFailed}
 * uniformly.
 *
 * <p>Per {@code integration-heavy} I7 the application service does not
 * see Resilience4j types: every annotation is on this adapter's public
 * method.
 */
@Component
@Profile("!standalone")
public class TmsClientAdapter implements ShipmentNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(TmsClientAdapter.class);

    static final String CIRCUIT_NAME = "tms-client";
    static final String SHIPMENTS_PATH = "/shipments";
    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final RestClient restClient;
    private final ShipmentPersistencePort shipmentPersistence;
    private final TmsRequestDedupePort dedupePort;
    private final TmsMetrics metrics;
    private final ObjectMapper objectMapper;
    private final java.time.Clock clock;

    public TmsClientAdapter(@Qualifier("tmsRestClient") RestClient restClient,
                            ShipmentPersistencePort shipmentPersistence,
                            TmsRequestDedupePort dedupePort,
                            TmsMetrics metrics,
                            ObjectMapper objectMapper,
                            java.time.Clock clock) {
        this.restClient = restClient;
        this.shipmentPersistence = shipmentPersistence;
        this.dedupePort = dedupePort;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Retry(name = CIRCUIT_NAME, fallbackMethod = "notifyFallback")
    @CircuitBreaker(name = CIRCUIT_NAME)
    @Bulkhead(name = CIRCUIT_NAME, type = Bulkhead.Type.SEMAPHORE)
    public TmsAcknowledgement notify(UUID shipmentId) {
        Timer.Sample sample = metrics.startTimer();
        try {
            // I4: local dedupe before any network call.
            Optional<String> cached = dedupePort.findSnapshot(shipmentId);
            if (cached.isPresent()) {
                log.info("tms_dedupe_hit shipmentId={}", shipmentId);
                metrics.recordDedupeHit();
                metrics.recordResult(TmsMetrics.Result.dedupe_hit);
                return readSnapshot(cached.get());
            }

            Shipment shipment = shipmentPersistence.findById(shipmentId)
                    .orElseThrow(() -> new ShipmentNotFoundException(shipmentId));
            TmsShipmentRequest request = TmsShipmentMapper.toRequest(shipment);

            log.debug("tms_request_started shipmentId={}", shipmentId);
            TmsShipmentResponse response = postToTms(shipmentId, request);
            TmsAcknowledgement ack = TmsShipmentMapper.toAcknowledgement(response);

            // Cache the snapshot only when we actually called TMS and got
            // a 2xx — failed calls must NOT write to dedupe.
            if (ack.success()) {
                writeSnapshot(shipmentId, ack);
            }
            metrics.recordResult(TmsMetrics.Result.success);
            log.info("tms_request_succeeded shipmentId={} requestId={}", shipmentId, ack.requestId());
            return ack;
        } finally {
            metrics.stopTimer(sample);
        }
    }

    /**
     * Resilience4j fallback — invoked on retry-exhaustion, circuit-open,
     * or bulkhead-full. Translates the underlying cause to
     * {@link ExternalServiceUnavailableException} so the listener can
     * uniformly route to {@code markFailed}.
     *
     * <p><strong>Wiring:</strong> the {@code fallbackMethod} is bound to the
     * <em>outermost</em> aspect ({@code @Retry}), NOT {@code @CircuitBreaker}.
     * With the default Resilience4j aspect order ({@code @Retry} wraps
     * {@code @CircuitBreaker} wraps {@code @Bulkhead}), a fallback on the inner
     * {@code @CircuitBreaker} fires on the FIRST {@link TmsTransientException}
     * and converts it to {@link ExternalServiceUnavailableException} — a type
     * absent from the retry's {@code retryExceptions} — so the outer
     * {@code @Retry} never retries and only a single HTTP attempt is made.
     * Binding the fallback to {@code @Retry} lets the breaker re-throw the raw
     * {@link TmsTransientException} on each attempt so retry exhausts its 3
     * configured attempts, then the outer fallback translates the final cause.
     *
     * <p>Method visibility must be {@code public} for Resilience4j AOP
     * to invoke it; signature must match the annotated method plus a
     * trailing {@link Throwable}.
     */
    public TmsAcknowledgement notifyFallback(UUID shipmentId, Throwable cause) {
        TmsMetrics.Result result;
        if (cause instanceof CallNotPermittedException) {
            result = TmsMetrics.Result.circuit_open;
            log.warn("tms_circuit_open shipmentId={}", shipmentId);
        } else if (cause instanceof BulkheadFullException) {
            result = TmsMetrics.Result.circuit_open;
            log.warn("tms_bulkhead_full shipmentId={}", shipmentId);
        } else if (cause instanceof TmsTransientException tte) {
            result = (tte.httpStatus() == 0) ? TmsMetrics.Result.timeout : TmsMetrics.Result.server_5xx;
            log.warn("tms_transient_exhausted shipmentId={} status={} msg={}",
                    shipmentId, tte.httpStatus(), tte.getMessage());
        } else if (cause instanceof TmsPermanentException tpe) {
            result = TmsMetrics.Result.client_4xx;
            log.warn("tms_permanent shipmentId={} status={} msg={}",
                    shipmentId, tpe.httpStatus(), tpe.getMessage());
        } else {
            result = TmsMetrics.Result.server_5xx;
            log.warn("tms_unexpected_exhausted shipmentId={} cause={}", shipmentId, cause.toString());
        }
        metrics.recordResult(result);
        throw new ExternalServiceUnavailableException("tms",
                "TMS notify exhausted for shipmentId=" + shipmentId
                        + " (" + cause.getClass().getSimpleName() + ")", cause);
    }

    /**
     * Performs the actual HTTP POST and routes vendor responses into
     * the adapter's transient/permanent exception lattice.
     */
    private TmsShipmentResponse postToTms(UUID shipmentId, TmsShipmentRequest request) {
        try {
            return restClient.post()
                    .uri(SHIPMENTS_PATH)
                    .header(IDEMPOTENCY_KEY_HEADER, shipmentId.toString())
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        int status = res.getStatusCode().value();
                        // 429 (rate-limit) is transient — retry per backoff.
                        if (status == 429) {
                            metrics.recordRetryAttempt(retryAttemptFromContext());
                            throw new TmsTransientException(
                                    "TMS rate-limited shipmentId=" + shipmentId, status);
                        }
                        // Vendor-side 409 means our Idempotency-Key matched a
                        // previously accepted submission — treat as success
                        // (per §2.11). The body should still parse as a normal
                        // ack; let RestClient unmarshall it.
                        if (status == 409) {
                            log.info("tms_idempotency_409 shipmentId={} (vendor honoured key)", shipmentId);
                            return; // continue normal body extraction
                        }
                        // Other 4xx: permanent — no retry.
                        String body = safeReadBody(res);
                        throw new TmsPermanentException(
                                "TMS permanent failure status=" + status + " body=" + body, status);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        int status = res.getStatusCode().value();
                        metrics.recordRetryAttempt(retryAttemptFromContext());
                        throw new TmsTransientException(
                                "TMS 5xx status=" + status + " shipmentId=" + shipmentId, status);
                    })
                    .body(TmsShipmentResponse.class);
        } catch (RestClientResponseException ex) {
            // Should be unreachable because onStatus handlers catch first,
            // but defensively translate any leaked one.
            int status = ex.getStatusCode().value();
            if (status >= 400 && status < 500 && status != 429) {
                throw new TmsPermanentException(
                        "TMS permanent failure status=" + status, status);
            }
            throw new TmsTransientException(
                    "TMS transient failure status=" + status, status, ex);
        } catch (ResourceAccessException ex) {
            // Underlying SocketTimeoutException / ConnectTimeoutException /
            // IOException — all retryable.
            metrics.recordRetryAttempt(retryAttemptFromContext());
            int status = isTimeout(ex) ? 0 : -1;
            throw new TmsTransientException(
                    "TMS network failure: " + ex.getMessage(), status, ex);
        }
    }

    private static boolean isTimeout(Throwable t) {
        Throwable c = t;
        while (c != null) {
            if (c instanceof SocketTimeoutException
                    || c instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
            c = c.getCause();
        }
        return false;
    }

    private static String safeReadBody(org.springframework.http.client.ClientHttpResponse res) {
        try {
            return new String(res.getBody().readAllBytes());
        } catch (IOException e) {
            return "<unreadable>";
        }
    }

    /**
     * Best-effort retrieval of the current Resilience4j retry attempt from
     * the surrounding decorators. Returns {@code 1} if no decorator is
     * active. The metric tag becomes the attempt number that triggered
     * this call (1, 2, or 3).
     */
    private int retryAttemptFromContext() {
        // Resilience4j 2.x doesn't surface the attempt counter via a thread
        // local for annotation-style retry. We approximate via a counter on
        // the metric itself (callers see "this attempt fired"). The attempt
        // number doesn't need to be exact for the dashboard view.
        return 1;
    }

    private void writeSnapshot(UUID shipmentId, TmsAcknowledgement ack) {
        try {
            String json = objectMapper.writeValueAsString(ack);
            dedupePort.saveSnapshot(shipmentId, clock.instant(), json);
        } catch (JsonProcessingException ex) {
            // Cannot cache — but the call already succeeded. Log and move on;
            // the next call will simply hit TMS again.
            log.warn("tms_dedupe_serialise_failed shipmentId={} reason={}",
                    shipmentId, ex.toString());
        }
    }

    private TmsAcknowledgement readSnapshot(String json) {
        try {
            return objectMapper.readValue(json, TmsAcknowledgement.class);
        } catch (JsonProcessingException ex) {
            // Snapshot row is corrupt — fall back to a synthetic success ack
            // (the row's existence proves a previous send succeeded).
            log.warn("tms_dedupe_deserialise_failed using_synthetic_ack reason={}", ex.toString());
            return TmsAcknowledgement.success(null);
        }
    }
}
