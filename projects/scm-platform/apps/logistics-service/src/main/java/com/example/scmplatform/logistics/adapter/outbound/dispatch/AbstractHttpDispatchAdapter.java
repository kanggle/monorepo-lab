package com.example.scmplatform.logistics.adapter.outbound.dispatch;

import com.example.scmplatform.logistics.adapter.outbound.persistence.DispatchDedupeStore;
import com.example.scmplatform.logistics.application.port.outbound.DispatchAck;
import com.example.scmplatform.logistics.application.port.outbound.ShipmentDispatchPort;
import com.example.scmplatform.logistics.domain.error.ShipmentDispatchException;
import com.example.scmplatform.logistics.domain.model.Carrier;
import com.example.scmplatform.logistics.domain.model.Dispatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Shared HTTP dispatch template for the carrier-aggregator vendors (external-integrations.md §1/§2).
 * Holds the one sequence both vendors run verbatim — dedupe short-circuit → {@code toRequest} →
 * POST → 429 mapping → tracking-id guard → dedupe save → {@code toAck} — plus the shared
 * transport-failure translation, parameterised by per-vendor hooks.
 *
 * <h2>What this class deliberately does NOT unify (TASK-SCM-BE-051 § Out of Scope)</h2>
 * <ul>
 *   <li><b>Resilience4j instances (I9).</b> Each concrete adapter keeps its own
 *       {@code @CircuitBreaker}/{@code @Retry}/{@code @Bulkhead} instance names. A 굿스플로 outage
 *       must never open EasyPost's circuit.</li>
 *   <li><b>Connection pools (I9).</b> Each vendor is handed its own {@code RestClient} bean over
 *       its own {@code PoolingHttpClientConnectionManager}.</li>
 *   <li><b>Vendor DTOs (I8).</b> {@code REQ}/{@code RES} stay vendor-shaped and package-private.</li>
 *   <li><b>4xx semantics.</b> "4xx handling is per-adapter, not a shared invariant"
 *       (external-integrations.md § 409 handling) — the 429 exception is supplied per vendor, and
 *       an adapter may override {@link #translateFailure(Dispatch, Throwable)}.</li>
 * </ul>
 *
 * <h2>Spring AOP invariants (load-bearing — the two TASK-SCM-BE-042 retry lessons)</h2>
 * <ul>
 *   <li>{@link #doDispatch(Dispatch)} is {@code final} and carries <b>no</b> resilience annotation.
 *       The externally-called, proxied entry point stays the concrete adapter's {@code dispatch()};
 *       the call into this template is an ordinary intra-object call, so the retry / circuit /
 *       bulkhead aspects apply exactly once.</li>
 *   <li>Resilience4j resolves {@code fallbackMethod} <b>by name on the target class</b>, so each
 *       concrete adapter declares its own {@code dispatchFallback(Dispatch, Throwable)}; it is not
 *       inherited from here. This class only supplies the shared body via
 *       {@link #translateFailure(Dispatch, Throwable)}.</li>
 *   <li>{@code fallbackMethod} must sit on {@code @Retry} (the outermost aspect), never on
 *       {@code @CircuitBreaker} — on the middle aspect it fires per attempt and collapses the
 *       retry count from 3 to 1.</li>
 * </ul>
 *
 * @param <REQ> the vendor-shaped request DTO
 * @param <RES> the vendor-shaped response DTO
 */
abstract class AbstractHttpDispatchAdapter<REQ, RES> implements ShipmentDispatchPort {

    /** Both vendors expose the booking resource at the same path (§1.1 / §2.1). */
    private static final String SHIPMENTS_PATH = "/shipments";

    /** Resolves to the CONCRETE adapter class, so log categories are unchanged by the extraction. */
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final RestClient restClient;
    private final VendorShipmentMapper<REQ, RES> mapper;
    private final DispatchDedupeStore dedupeStore;
    private final Carrier vendor;
    private final String vendorLabel;
    private final Consumer<HttpHeaders> vendorHeaders;
    private final Supplier<RuntimeException> rateLimitedException;
    private final String missingTrackingIdMessage;

    /**
     * @param restClient               the vendor's <b>dedicated</b> pooled client (I9)
     * @param mapper                   the vendor's DTO translator (I8)
     * @param dedupeStore              the shared local idempotency ground-truth (I4)
     * @param vendor                   the {@link Carrier} stamped on the dedupe snapshot
     * @param vendorLabel              the vendor name used verbatim in log / exception messages
     * @param vendorHeaders            per-request auth headers; applied BEFORE {@code
     *                                 Idempotency-Key} / {@code Content-Type} so header order is
     *                                 unchanged. A no-op for a vendor whose auth is a default
     *                                 header on its own client (EasyPost's HTTP Basic).
     * @param rateLimitedException     supplies the vendor's distinct RETRYABLE 429 exception —
     *                                 referenced by FQN in {@code application.yml}'s per-vendor
     *                                 {@code retry-exceptions} / {@code record-exceptions}
     * @param missingTrackingIdMessage the message for a 2xx carrying no tracking id (a contract
     *                                 failure → permanent, never retried)
     */
    protected AbstractHttpDispatchAdapter(RestClient restClient,
                                          VendorShipmentMapper<REQ, RES> mapper,
                                          DispatchDedupeStore dedupeStore,
                                          Carrier vendor,
                                          String vendorLabel,
                                          Consumer<HttpHeaders> vendorHeaders,
                                          Supplier<RuntimeException> rateLimitedException,
                                          String missingTrackingIdMessage) {
        this.restClient = restClient;
        this.mapper = mapper;
        this.dedupeStore = dedupeStore;
        this.vendor = vendor;
        this.vendorLabel = vendorLabel;
        this.vendorHeaders = vendorHeaders;
        this.rateLimitedException = rateLimitedException;
        this.missingTrackingIdMessage = missingTrackingIdMessage;
    }

    /**
     * The shared dispatch sequence. <b>Intentionally {@code final} and un-annotated</b> — see the
     * AOP invariants in the class javadoc. Called only from a concrete adapter's annotated
     * {@code dispatch()}.
     */
    protected final DispatchAck doDispatch(Dispatch dispatch) {
        UUID requestId = dispatch.getShipmentId().value();

        Optional<String> cached = dedupeStore.findSnapshot(requestId);
        if (cached.isPresent()) {
            // Repeat send — cached ack, NO network call (I4).
            return mapper.ackFromSnapshot(cached.get());
        }

        REQ request = mapper.toRequest(dispatch);
        RES response = restClient.post()
                .uri(SHIPMENTS_PATH)
                // Vendor auth header hook (a no-op unless the vendor authenticates per-request).
                .headers(vendorHeaders)
                // Stable dedup key across resilience4j retry and operator :retry (§1.7/§2.7).
                .header("Idempotency-Key", requestId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                // 429 → a distinct RETRYABLE exception; every other 4xx falls through to the
                // default handler (HttpClientErrorException → ignored, non-retryable).
                .onStatus(status -> status.value() == 429, (req, res) -> {
                    throw rateLimitedException.get();
                })
                .body(mapper.responseType());

        String trackingCode = response == null ? null : mapper.trackingCode(response);
        if (trackingCode == null || trackingCode.isBlank()) {
            // A 2xx with no tracking id is a contract failure, not a vendor outage → permanent.
            throw new ShipmentDispatchException(missingTrackingIdMessage, false, null);
        }

        dedupeStore.save(requestId, vendor, mapper.serialize(response));
        return mapper.toAck(response);
    }

    /**
     * The shared Resilience4j-fallback body — reachable on circuit OPEN, retries exhausted, a
     * permanent 4xx, timeout/IO, or bulkhead-full. Translates the transport/resilience failure into
     * a domain {@link ShipmentDispatchException}; an already-domain-shaped failure (the "no tracking
     * id" case) is re-thrown as-is.
     *
     * <p>Never returns normally — the {@link DispatchAck} return type exists so a concrete
     * adapter's own {@code dispatchFallback} can {@code return} this call.
     */
    protected DispatchAck translateFailure(Dispatch dispatch, Throwable t) {
        if (t instanceof ShipmentDispatchException sde) {
            throw sde;
        }
        boolean retryable = !(t instanceof HttpClientErrorException);
        log.warn("{} dispatch failed for shipment {} ({}: {})",
                vendorLabel, dispatch.getShipmentId(), t.getClass().getSimpleName(), t.getMessage());
        throw new ShipmentDispatchException(
                vendorLabel + " dispatch failed: " + t.getMessage(), retryable, t);
    }
}
