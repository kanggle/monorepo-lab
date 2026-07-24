package com.example.scmplatform.logistics.adapter.outbound.dispatch;

import com.example.scmplatform.logistics.adapter.outbound.persistence.DispatchDedupeStore;
import com.example.scmplatform.logistics.application.port.outbound.DispatchAck;
import com.example.scmplatform.logistics.application.port.outbound.ShipmentDispatchPort;
import com.example.scmplatform.logistics.config.GoodsflowClientProperties;
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
 * 굿스플로 (Goodsflow) domestic carrier-aggregator dispatch adapter (external-integrations.md §2).
 * Pushes a confirmed domestic-route shipment to 굿스플로 (접수/운송장 발행) and returns a
 * vendor-neutral {@link DispatchAck}. The sibling of {@code EasyPostDispatchAdapter} for KR
 * carriers (CJ대한통운/한진/롯데/우체국/…); selected per shipment by {@code CarrierRouter}.
 *
 * <ul>
 *   <li><b>Idempotency (I4).</b> {@code Idempotency-Key = shipment.id}; the <b>same</b> local
 *       {@link DispatchDedupeStore} short-circuits a repeat send with the cached snapshot and
 *       <b>no network call</b> — the snapshot records {@link Carrier#GOODSFLOW} so a shipment
 *       cannot be double-dispatched across vendors (§2.7).</li>
 *   <li><b>Resilience (I2/I3/I9).</b> A <b>dedicated, independent</b> {@code goodsflowDispatch}
 *       circuit / retry / bulkhead — SEPARATE instances from EasyPost's (I9: "no pool shared
 *       across vendors"; a 굿스플로 outage must not open EasyPost's circuit). 429 → retried;
 *       other 4xx → not retried; 5xx/timeout/IO → retried then circuit.</li>
 *   <li><b>Dedicated pool (I9).</b> Runs over the {@code goodsflowRestClient} Apache HttpClient 5
 *       pool — not shared with EasyPost or any other vendor.</li>
 * </ul>
 *
 * Active under every profile except {@code standalone} (which swaps in the credential-free stub).
 */
@Component
@Profile("!standalone")
public class GoodsflowDispatchAdapter implements ShipmentDispatchPort {

    private static final Logger log = LoggerFactory.getLogger(GoodsflowDispatchAdapter.class);

    private final RestClient goodsflowRestClient;
    private final GoodsflowShipmentMapper mapper;
    private final DispatchDedupeStore dedupeStore;
    private final String apiKeyHeaderName;
    private final String apiKey;

    public GoodsflowDispatchAdapter(@Qualifier("goodsflowRestClient") RestClient goodsflowRestClient,
                                    GoodsflowShipmentMapper mapper,
                                    DispatchDedupeStore dedupeStore,
                                    GoodsflowClientProperties props) {
        this.goodsflowRestClient = goodsflowRestClient;
        this.mapper = mapper;
        this.dedupeStore = dedupeStore;
        this.apiKeyHeaderName = props.getApiKeyHeaderName();
        this.apiKey = props.getApiKey();
    }

    @Override
    // fallbackMethod is on @Retry (the OUTERMOST resilience4j aspect: Retry → CircuitBreaker →
    // Bulkhead). Keeping it here — not on @CircuitBreaker — is load-bearing: a fallback on the
    // middle CircuitBreaker aspect fires per-attempt and converts the retryable vendor exception
    // to a domain exception *before* @Retry can see it, collapsing the retry count. On the
    // outermost aspect it fires exactly once, after all retries are exhausted (or the circuit is
    // open / bulkhead full), so 429/5xx/timeout retry the full max-attempts=3 (§2.6). This is the
    // reapplication of the two BE-042 retry lessons for the 굿스플로 vendor.
    @CircuitBreaker(name = "goodsflowDispatch")
    @Retry(name = "goodsflowDispatch", fallbackMethod = "dispatchFallback")
    @Bulkhead(name = "goodsflowDispatch")
    public DispatchAck dispatch(Dispatch dispatch) {
        UUID requestId = dispatch.getShipmentId().value();

        Optional<String> cached = dedupeStore.findSnapshot(requestId);
        if (cached.isPresent()) {
            // Repeat send — cached ack, NO network call (I4).
            return mapper.ackFromSnapshot(cached.get());
        }

        GoodsflowShipmentRequest request = mapper.toRequest(dispatch);
        GoodsflowShipmentResponse response = goodsflowRestClient.post()
                .uri("/shipments")
                // 굿스플로 API-key header (§2.2) — the vendor-specified header name (configurable).
                .header(apiKeyHeaderName, apiKey)
                // Stable dedup key across resilience4j retry and operator :retry (§2.7).
                .header("Idempotency-Key", requestId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                // 429 → a distinct RETRYABLE exception; every other 4xx falls through to the
                // default handler (HttpClientErrorException → ignored, non-retryable).
                .onStatus(status -> status.value() == 429, (req, res) -> {
                    throw new GoodsflowRateLimitedException("굿스플로 returned 429 (rate limited)");
                })
                .body(GoodsflowShipmentResponse.class);

        if (response == null || response.trackingCode() == null || response.trackingCode().isBlank()) {
            // A 2xx with no 운송장번호 is a contract failure, not a vendor outage → permanent.
            throw new ShipmentDispatchException(
                    "굿스플로 accepted the shipment but returned no invoiceNo (운송장번호)", false, null);
        }

        dedupeStore.save(requestId, Carrier.GOODSFLOW, mapper.serialize(response));
        return mapper.toAck(response);
    }

    /**
     * Resilience4j fallback — reachable on circuit OPEN, retries exhausted, a permanent 4xx,
     * timeout/IO, or bulkhead-full. Translates the transport/resilience failure into a domain
     * {@link ShipmentDispatchException}; the "no 운송장번호" case is already domain-shaped and is
     * re-thrown as-is.
     */
    @SuppressWarnings("unused")
    public DispatchAck dispatchFallback(Dispatch dispatch, Throwable t) {
        if (t instanceof ShipmentDispatchException sde) {
            throw sde;
        }
        boolean retryable = !(t instanceof HttpClientErrorException);
        log.warn("굿스플로 dispatch failed for shipment {} ({}: {})",
                dispatch.getShipmentId(), t.getClass().getSimpleName(), t.getMessage());
        throw new ShipmentDispatchException(
                "굿스플로 dispatch failed: " + t.getMessage(), retryable, t);
    }
}
