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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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
 * <p>The HTTP sequence itself lives in {@link AbstractHttpDispatchAdapter} (TASK-SCM-BE-051); this
 * class supplies the EasyPost hooks and — critically — <b>keeps its own resilience annotations and
 * its own {@code dispatchFallback} declaration</b>. Auth is HTTP Basic applied as a default header
 * on {@code easyPostRestClient}, so no per-request auth header is contributed here (§1.2).
 *
 * Active under every profile except {@code standalone} (which swaps in the credential-free stub).
 */
@Component
@Profile("!standalone")
public class EasyPostDispatchAdapter
        extends AbstractHttpDispatchAdapter<EasyPostShipmentRequest, EasyPostShipmentResponse>
        implements ShipmentDispatchPort {

    public EasyPostDispatchAdapter(@Qualifier("easyPostRestClient") RestClient easyPostRestClient,
                                   EasyPostShipmentMapper mapper,
                                   DispatchDedupeStore dedupeStore) {
        super(easyPostRestClient,
                mapper,
                dedupeStore,
                Carrier.EASYPOST,
                "EasyPost",
                // No per-request auth header — HTTP Basic is a default header on the EasyPost
                // client (§1.2), and the 굿스플로 API-key header must never appear on an
                // EasyPost POST (separate RestClient beans make that structural).
                headers -> {
                },
                () -> new EasyPostRateLimitedException("EasyPost returned 429 (rate limited)"),
                "EasyPost accepted the shipment but returned no tracking_code");
    }

    @Override
    // fallbackMethod is on @Retry (the OUTERMOST resilience4j aspect: Retry → CircuitBreaker →
    // Bulkhead). Keeping it here — not on @CircuitBreaker — is load-bearing: a fallback on the
    // middle CircuitBreaker aspect fires per-attempt and converts the retryable vendor exception
    // to a domain exception *before* @Retry can see it, collapsing the retry count. On the
    // outermost aspect it fires exactly once, after all retries are exhausted (or the circuit is
    // open / bulkhead full), so 429/5xx/timeout retry the full max-attempts=3 (external-integrations.md §1.6).
    //
    // This method — not the shared template it calls — is the proxied entry point. The
    // doDispatch(...) call below is an ordinary intra-object call (Spring AOP self-invocation), so
    // the aspects apply exactly once; doDispatch is final and carries no annotation.
    @CircuitBreaker(name = "easyPostDispatch")
    @Retry(name = "easyPostDispatch", fallbackMethod = "dispatchFallback")
    @Bulkhead(name = "easyPostDispatch")
    public DispatchAck dispatch(Dispatch dispatch) {
        return doDispatch(dispatch);
    }

    /**
     * Resilience4j fallback — reachable on circuit OPEN, retries exhausted, a permanent 4xx,
     * timeout/IO, or bulkhead-full. Declared HERE on purpose: resilience4j resolves
     * {@code fallbackMethod} by name on the target class, not through inheritance. The body is the
     * shared translation ("no tracking_code" is already domain-shaped and is re-thrown as-is).
     */
    @SuppressWarnings("unused")
    public DispatchAck dispatchFallback(Dispatch dispatch, Throwable t) {
        return translateFailure(dispatch, t);
    }
}
