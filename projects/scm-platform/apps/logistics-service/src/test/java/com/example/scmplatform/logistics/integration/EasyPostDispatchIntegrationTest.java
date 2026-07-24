package com.example.scmplatform.logistics.integration;

import com.example.scmplatform.logistics.application.port.outbound.DispatchAck;
import com.example.scmplatform.logistics.application.port.outbound.ShipmentDispatchPort;
import com.example.scmplatform.logistics.application.usecase.DispatchShipmentUseCase;
import com.example.scmplatform.logistics.domain.model.Dispatch;
import com.example.scmplatform.logistics.domain.model.DispatchStatus;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.moreThanOrExactly;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * WireMock EasyPost dispatch matrix (external-integrations.md §8, I10): success, 4xx (no retry),
 * 429 (retry), 5xx (retry → circuit), timeout, bulkhead-full, and idempotency-replay (2nd send
 * with the same key → cached ack, no 2nd WireMock call).
 */
class EasyPostDispatchIntegrationTest extends AbstractLogisticsIntegrationTest {

    private static final String SHIPMENTS = "/shipments";
    private static final String SUCCESS_BODY =
            "{\"id\":\"shp_1\",\"tracking_code\":\"TRACK-1\","
                    + "\"selected_rate\":{\"carrier\":\"USPS\"},\"status\":\"purchased\"}";

    @Autowired
    private DispatchShipmentUseCase dispatchShipmentUseCase;

    @Autowired
    @Qualifier("easyPostDispatchAdapter")
    private ShipmentDispatchPort shipmentDispatchPort; // the EasyPostDispatchAdapter bean (proxied)

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void reset() {
        cleanDatabase();
        EASYPOST.resetAll();
        circuitBreakerRegistry.circuitBreaker("easyPostDispatch").reset();
    }

    @Test
    void success_201_transitionsToDispatchedWithTracking() {
        EASYPOST.stubFor(post(urlPathEqualTo(SHIPMENTS))
                .willReturn(aResponse().withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(SUCCESS_BODY)));

        Dispatch seeded = seedPending(UUID.randomUUID(), "SHP-OK");
        Dispatch result = dispatchShipmentUseCase.dispatch(seeded);

        assertThat(result.getStatus()).isEqualTo(DispatchStatus.DISPATCHED);
        assertThat(result.getTrackingNo().value()).isEqualTo("TRACK-1");
        assertThat(result.getCarrierCode().value()).isEqualTo("USPS");
        EASYPOST.verify(exactly(1), postRequestedFor(urlPathEqualTo(SHIPMENTS)));
    }

    @Test
    void clientError_400_noRetry_transitionsToFailed() {
        EASYPOST.stubFor(post(urlPathEqualTo(SHIPMENTS))
                .willReturn(aResponse().withStatus(400)));

        Dispatch result = dispatchShipmentUseCase.dispatch(seedPending(UUID.randomUUID(), "SHP-400"));

        assertThat(result.getStatus()).isEqualTo(DispatchStatus.DISPATCH_FAILED);
        // 4xx is permanent — no retry.
        EASYPOST.verify(exactly(1), postRequestedFor(urlPathEqualTo(SHIPMENTS)));
    }

    @Test
    void rateLimited_429_retried_thenFailed() {
        EASYPOST.stubFor(post(urlPathEqualTo(SHIPMENTS))
                .willReturn(aResponse().withStatus(429)));

        Dispatch result = dispatchShipmentUseCase.dispatch(seedPending(UUID.randomUUID(), "SHP-429"));

        assertThat(result.getStatus()).isEqualTo(DispatchStatus.DISPATCH_FAILED);
        // 429 IS retried (maxAttempts=3).
        EASYPOST.verify(exactly(3), postRequestedFor(urlPathEqualTo(SHIPMENTS)));
    }

    @Test
    void serverError_5xx_retried_thenFailed() {
        EASYPOST.stubFor(post(urlPathEqualTo(SHIPMENTS))
                .willReturn(aResponse().withStatus(503)));

        Dispatch result = dispatchShipmentUseCase.dispatch(seedPending(UUID.randomUUID(), "SHP-503"));

        assertThat(result.getStatus()).isEqualTo(DispatchStatus.DISPATCH_FAILED);
        EASYPOST.verify(moreThanOrExactly(2), postRequestedFor(urlPathEqualTo(SHIPMENTS)));
    }

    @Test
    void timeout_transitionsToFailed() {
        // read-timeout is 2s (IT base); a 4s delay forces a read timeout.
        EASYPOST.stubFor(post(urlPathEqualTo(SHIPMENTS))
                .willReturn(aResponse().withStatus(201)
                        .withFixedDelay(4000)
                        .withBody(SUCCESS_BODY)));

        Dispatch result = dispatchShipmentUseCase.dispatch(seedPending(UUID.randomUUID(), "SHP-TO"));

        assertThat(result.getStatus()).isEqualTo(DispatchStatus.DISPATCH_FAILED);
    }

    @Test
    void repeated5xx_opensCircuit() {
        EASYPOST.stubFor(post(urlPathEqualTo(SHIPMENTS))
                .willReturn(aResponse().withStatus(503)));

        // Each dispatch is up to 3 CB calls (retry); minimumNumberOfCalls=10 at 100% failure
        // opens the circuit within a handful of dispatches.
        for (int i = 0; i < 10; i++) {
            dispatchShipmentUseCase.dispatch(seedPending(UUID.randomUUID(), "SHP-CB-" + i));
        }

        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("easyPostDispatch");
        assertThat(cb.getState())
                .isIn(CircuitBreaker.State.OPEN, CircuitBreaker.State.FORCED_OPEN);
    }

    @Test
    void idempotencyReplay_secondSendReturnsCachedAck_noSecondVendorCall() {
        EASYPOST.stubFor(post(urlPathEqualTo(SHIPMENTS))
                .willReturn(aResponse().withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(SUCCESS_BODY)));

        Dispatch seeded = seedPending(UUID.randomUUID(), "SHP-DUP");

        DispatchAck first = shipmentDispatchPort.dispatch(seeded);
        DispatchAck second = shipmentDispatchPort.dispatch(seeded); // same shipmentId → dedupe

        assertThat(first.trackingNo()).isEqualTo("TRACK-1");
        assertThat(second.trackingNo()).isEqualTo("TRACK-1");
        // The dedupe short-circuit means EasyPost was called exactly once.
        EASYPOST.verify(exactly(1), postRequestedFor(urlPathEqualTo(SHIPMENTS)));
    }

    @Test
    void bulkheadFull_underConcurrency_rejectsSomeCalls() throws Exception {
        // Slow stub so calls stay in-flight and saturate the 10-slot bulkhead.
        EASYPOST.stubFor(post(urlPathEqualTo(SHIPMENTS))
                .willReturn(aResponse().withStatus(201)
                        .withFixedDelay(800)
                        .withHeader("Content-Type", "application/json")
                        .withBody(SUCCESS_BODY)));

        int concurrency = 16;
        List<Dispatch> seeds = new ArrayList<>();
        for (int i = 0; i < concurrency; i++) {
            seeds.add(seedPending(UUID.randomUUID(), "SHP-BH-" + i));
        }

        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        try {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (Dispatch seed : seeds) {
                tasks.add(() -> {
                    try {
                        shipmentDispatchPort.dispatch(seed);
                        return true;   // accepted
                    } catch (RuntimeException e) {
                        return false;  // rejected (bulkhead full → ShipmentDispatchException)
                    }
                });
            }
            List<Future<Boolean>> futures = pool.invokeAll(tasks);
            long rejected = 0;
            for (Future<Boolean> f : futures) {
                if (!f.get()) {
                    rejected++;
                }
            }
            // With 16 concurrent calls against a 10-slot fail-fast bulkhead, at least one is
            // rejected. (CI Linux is authority; exact count is timing-dependent.)
            assertThat(rejected).isGreaterThanOrEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
