package com.example.scmplatform.logistics.adapter.outbound.dispatch;

import com.example.scmplatform.logistics.adapter.outbound.persistence.DispatchDedupeStore;
import com.example.scmplatform.logistics.application.port.outbound.DispatchAck;
import com.example.scmplatform.logistics.application.port.outbound.ShipmentDispatchPort;
import com.example.scmplatform.logistics.domain.error.ShipmentDispatchException;
import com.example.scmplatform.logistics.domain.model.Carrier;
import com.example.scmplatform.logistics.domain.model.Dispatch;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.UUID;

/**
 * EasyPost carrier-aggregator dispatch adapter (external-integrations.md §1). Pushes a confirmed
 * shipment to EasyPost and returns a vendor-neutral {@link DispatchAck}.
 *
 * <ul>
 *   <li><b>Idempotency (I4).</b> {@code Idempotency-Key = shipment.id}; the local
 *       {@link DispatchDedupeStore} short-circuits a repeat send with the cached snapshot and
 *       <b>no network call</b>.</li>
 *   <li><b>Resilience (I2/I3/I9).</b> Dedicated {@code easyPostDispatch} circuit / retry /
 *       bulkhead. 429 → retried; other 4xx → not retried; 5xx/timeout/IO → retried then circuit.
 *       On any exhaustion the fallback raises a domain {@link ShipmentDispatchException} → the
 *       use case records {@code DISPATCH_FAILED} (never a consume failure, S5).</li>
 *   <li><b>Dedicated pool (I9).</b> Runs over the {@code easyPostRestClient} Apache HttpClient 5
 *       pool — not shared with any other vendor.</li>
 * </ul>
 *
 * Active under every profile except {@code standalone} (which swaps in the credential-free stub).
 */
@Component
@Profile("!standalone")
public class EasyPostDispatchAdapter implements ShipmentDispatchPort {

    private static final Logger log = LoggerFactory.getLogger(EasyPostDispatchAdapter.class);

    private final RestClient easyPostRestClient;
    private final EasyPostShipmentMapper mapper;
    private final DispatchDedupeStore dedupeStore;

    public EasyPostDispatchAdapter(@Qualifier("easyPostRestClient") RestClient easyPostRestClient,
                                   EasyPostShipmentMapper mapper,
                                   DispatchDedupeStore dedupeStore) {
        this.easyPostRestClient = easyPostRestClient;
        this.mapper = mapper;
        this.dedupeStore = dedupeStore;
    }

    @Override
    @CircuitBreaker(name = "easyPostDispatch", fallbackMethod = "dispatchFallback")
    @Retry(name = "easyPostDispatch")
    @Bulkhead(name = "easyPostDispatch")
    public DispatchAck dispatch(Dispatch dispatch) {
        UUID requestId = dispatch.getShipmentId().value();

        Optional<String> cached = dedupeStore.findSnapshot(requestId);
        if (cached.isPresent()) {
            // Repeat send — cached ack, NO network call (I4).
            return mapper.ackFromSnapshot(cached.get());
        }

        EasyPostShipmentRequest request = mapper.toRequest(dispatch);
        EasyPostShipmentResponse response = easyPostRestClient.post()
                .uri("/shipments")
                .header("Idempotency-Key", requestId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                // 429 → a distinct RETRYABLE exception; every other 4xx falls through to the
                // default handler (HttpClientErrorException → ignored, non-retryable).
                .onStatus(status -> status.value() == 429, (req, res) -> {
                    throw new EasyPostRateLimitedException("EasyPost returned 429 (rate limited)");
                })
                .body(EasyPostShipmentResponse.class);

        if (response == null || response.trackingCode() == null || response.trackingCode().isBlank()) {
            // A 2xx with no tracking_code is a contract failure, not a vendor outage → permanent.
            throw new ShipmentDispatchException(
                    "EasyPost accepted the shipment but returned no tracking_code", false, null);
        }

        dedupeStore.save(requestId, Carrier.EASYPOST, mapper.serialize(response));
        return mapper.toAck(response);
    }

    /**
     * Resilience4j fallback — reachable on circuit OPEN, retries exhausted, a permanent 4xx,
     * timeout/IO, or bulkhead-full. Translates the transport/resilience failure into a domain
     * {@link ShipmentDispatchException}; the "no tracking_code" case is already domain-shaped and
     * is re-thrown as-is.
     */
    @SuppressWarnings("unused")
    public DispatchAck dispatchFallback(Dispatch dispatch, Throwable t) {
        if (t instanceof ShipmentDispatchException sde) {
            throw sde;
        }
        boolean retryable = !(t instanceof HttpClientErrorException);
        log.warn("EasyPost dispatch failed for shipment {} ({}: {})",
                dispatch.getShipmentId(), t.getClass().getSimpleName(), t.getMessage());
        throw new ShipmentDispatchException(
                "EasyPost dispatch failed: " + t.getMessage(), retryable, t);
    }
}
