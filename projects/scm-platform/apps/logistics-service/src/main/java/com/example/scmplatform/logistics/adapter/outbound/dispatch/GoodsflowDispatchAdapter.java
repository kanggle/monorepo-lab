package com.example.scmplatform.logistics.adapter.outbound.dispatch;

import com.example.scmplatform.logistics.adapter.outbound.persistence.DispatchDedupeStore;
import com.example.scmplatform.logistics.application.port.outbound.DispatchAck;
import com.example.scmplatform.logistics.application.port.outbound.ShipmentDispatchPort;
import com.example.scmplatform.logistics.config.GoodsflowClientProperties;
import com.example.scmplatform.logistics.domain.model.Carrier;
import com.example.scmplatform.logistics.domain.model.Dispatch;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.function.Consumer;

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
 * <p>The HTTP sequence itself lives in {@link AbstractHttpDispatchAdapter} (TASK-SCM-BE-051);
 * sharing the template shares <b>no</b> resilience instance, pool, DTO or 4xx rule with EasyPost.
 * This class supplies the 굿스플로 hooks and — critically — <b>keeps its own resilience annotations
 * and its own {@code dispatchFallback} declaration</b>.
 *
 * Active under every profile except {@code standalone} (which swaps in the credential-free stub).
 */
@Component
@Profile("!standalone")
public class GoodsflowDispatchAdapter
        extends AbstractHttpDispatchAdapter<GoodsflowShipmentRequest, GoodsflowShipmentResponse>
        implements ShipmentDispatchPort {

    public GoodsflowDispatchAdapter(@Qualifier("goodsflowRestClient") RestClient goodsflowRestClient,
                                    GoodsflowShipmentMapper mapper,
                                    DispatchDedupeStore dedupeStore,
                                    GoodsflowClientProperties props) {
        super(goodsflowRestClient,
                mapper,
                dedupeStore,
                Carrier.GOODSFLOW,
                "굿스플로",
                apiKeyHeader(props),
                () -> new GoodsflowRateLimitedException("굿스플로 returned 429 (rate limited)"),
                "굿스플로 accepted the shipment but returned no invoiceNo (운송장번호)");
    }

    /**
     * 굿스플로 API-key header (§2.2) — the vendor-specified header name (configurable). Snapshotted
     * at construction, exactly as the pre-BE-051 adapter fields were, and applied by the shared
     * template <b>before</b> {@code Idempotency-Key}/{@code Content-Type} so header order and
     * content are unchanged. EasyPost contributes no such header (its auth is a default header on
     * its own client), so this key can never ride an EasyPost POST.
     */
    private static Consumer<HttpHeaders> apiKeyHeader(GoodsflowClientProperties props) {
        String apiKeyHeaderName = props.getApiKeyHeaderName();
        String apiKey = props.getApiKey();
        return headers -> headers.add(apiKeyHeaderName, apiKey);
    }

    @Override
    // fallbackMethod is on @Retry (the OUTERMOST resilience4j aspect: Retry → CircuitBreaker →
    // Bulkhead). Keeping it here — not on @CircuitBreaker — is load-bearing: a fallback on the
    // middle CircuitBreaker aspect fires per-attempt and converts the retryable vendor exception
    // to a domain exception *before* @Retry can see it, collapsing the retry count. On the
    // outermost aspect it fires exactly once, after all retries are exhausted (or the circuit is
    // open / bulkhead full), so 429/5xx/timeout retry the full max-attempts=3 (§2.6). This is the
    // reapplication of the two BE-042 retry lessons for the 굿스플로 vendor.
    //
    // This method — not the shared template it calls — is the proxied entry point. The
    // doDispatch(...) call below is an ordinary intra-object call (Spring AOP self-invocation), so
    // the aspects apply exactly once; doDispatch is final and carries no annotation.
    @CircuitBreaker(name = "goodsflowDispatch")
    @Retry(name = "goodsflowDispatch", fallbackMethod = "dispatchFallback")
    @Bulkhead(name = "goodsflowDispatch")
    public DispatchAck dispatch(Dispatch dispatch) {
        return doDispatch(dispatch);
    }

    /**
     * Resilience4j fallback — reachable on circuit OPEN, retries exhausted, a permanent 4xx,
     * timeout/IO, or bulkhead-full. Declared HERE on purpose: resilience4j resolves
     * {@code fallbackMethod} by name on the target class, not through inheritance. The body is the
     * shared translation (the "no 운송장번호" case is already domain-shaped and is re-thrown as-is).
     */
    @SuppressWarnings("unused")
    public DispatchAck dispatchFallback(Dispatch dispatch, Throwable t) {
        return translateFailure(dispatch, t);
    }
}
