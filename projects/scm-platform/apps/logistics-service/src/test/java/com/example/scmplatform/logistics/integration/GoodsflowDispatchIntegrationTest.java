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
 * WireMock 굿스플로 dispatch matrix (external-integrations.md §8, I10) — the independent mirror of
 * {@link EasyPostDispatchIntegrationTest}, driven against the SEPARATE {@code GOODSFLOW} stub:
 * success, 4xx (no retry), 429 (retry — the retry-count guard for the two BE-042 retry lessons),
 * 5xx (retry → circuit), timeout, bulkhead-full, idempotency-replay, and the I9 isolation check
 * (a 굿스플로 circuit-open does NOT trip EasyPost's circuit).
 *
 * <p>Dispatches are seeded with a domestic {@code requestedCarrierCode} ({@code CJ-LOGISTICS}) so
 * the {@code CarrierRouter} selects 굿스플로 — and the EasyPost stub is asserted untouched.
 */
class GoodsflowDispatchIntegrationTest extends AbstractLogisticsIntegrationTest {

    private static final String SHIPMENTS = "/shipments";
    private static final String DOMESTIC = "CJ-LOGISTICS";
    private static final String SUCCESS_BODY =
            "{\"id\":\"gf_1\",\"invoiceNo\":\"INV-1\","
                    + "\"deliveryCompanyCode\":\"CJ-GLS\",\"status\":\"BOOKED\"}";

    @Autowired
    private DispatchShipmentUseCase dispatchShipmentUseCase;

    @Autowired
    @Qualifier("goodsflowDispatchAdapter")
    private ShipmentDispatchPort goodsflowPort; // the GoodsflowDispatchAdapter bean (proxied)

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void reset() {
        cleanDatabase();
        EASYPOST.resetAll();
        GOODSFLOW.resetAll();
        circuitBreakerRegistry.circuitBreaker("goodsflowDispatch").reset();
        circuitBreakerRegistry.circuitBreaker("easyPostDispatch").reset();
    }

    @Test
    void success_transitionsToDispatchedWithWaybill_andRoutesToGoodsflowNotEasyPost() {
        GOODSFLOW.stubFor(post(urlPathEqualTo(SHIPMENTS))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(SUCCESS_BODY)));

        Dispatch seeded = seedPending(UUID.randomUUID(), "SHP-KR-OK", DOMESTIC);
        Dispatch result = dispatchShipmentUseCase.dispatch(seeded);

        assertThat(result.getStatus()).isEqualTo(DispatchStatus.DISPATCHED);
        assertThat(result.getTrackingNo().value()).isEqualTo("INV-1");
        assertThat(result.getCarrierCode().value()).isEqualTo("CJ-GLS");
        GOODSFLOW.verify(exactly(1), postRequestedFor(urlPathEqualTo(SHIPMENTS)));
        // Routed to 굿스플로 — EasyPost was NOT called.
        EASYPOST.verify(exactly(0), postRequestedFor(urlPathEqualTo(SHIPMENTS)));
    }

    @Test
    void clientError_400_noRetry_transitionsToFailed() {
        GOODSFLOW.stubFor(post(urlPathEqualTo(SHIPMENTS))
                .willReturn(aResponse().withStatus(400)));

        Dispatch result = dispatchShipmentUseCase.dispatch(
                seedPending(UUID.randomUUID(), "SHP-KR-400", DOMESTIC));

        assertThat(result.getStatus()).isEqualTo(DispatchStatus.DISPATCH_FAILED);
        // 4xx is permanent — no retry.
        GOODSFLOW.verify(exactly(1), postRequestedFor(urlPathEqualTo(SHIPMENTS)));
    }

    @Test
    void rateLimited_429_retriedExactlyMaxAttempts_thenFailed() {
        GOODSFLOW.stubFor(post(urlPathEqualTo(SHIPMENTS))
                .willReturn(aResponse().withStatus(429)));

        Dispatch result = dispatchShipmentUseCase.dispatch(
                seedPending(UUID.randomUUID(), "SHP-KR-429", DOMESTIC));

        assertThat(result.getStatus()).isEqualTo(DispatchStatus.DISPATCH_FAILED);
        // THE RETRY-LESSON GUARD: 429 → EXACTLY maxAttempts=3 vendor calls. If the fallback were
        // on @CircuitBreaker (middle aspect) or HttpClient auto-retries were left on, this count
        // would collapse or inflate — the two BE-042 CI-RED rounds.
        GOODSFLOW.verify(exactly(3), postRequestedFor(urlPathEqualTo(SHIPMENTS)));
    }

    @Test
    void serverError_5xx_retried_thenFailed() {
        GOODSFLOW.stubFor(post(urlPathEqualTo(SHIPMENTS))
                .willReturn(aResponse().withStatus(503)));

        Dispatch result = dispatchShipmentUseCase.dispatch(
                seedPending(UUID.randomUUID(), "SHP-KR-503", DOMESTIC));

        assertThat(result.getStatus()).isEqualTo(DispatchStatus.DISPATCH_FAILED);
        GOODSFLOW.verify(moreThanOrExactly(2), postRequestedFor(urlPathEqualTo(SHIPMENTS)));
    }

    @Test
    void timeout_transitionsToFailed() {
        // read-timeout is 2s (IT base); a 4s delay forces a read timeout.
        GOODSFLOW.stubFor(post(urlPathEqualTo(SHIPMENTS))
                .willReturn(aResponse().withStatus(200)
                        .withFixedDelay(4000)
                        .withBody(SUCCESS_BODY)));

        Dispatch result = dispatchShipmentUseCase.dispatch(
                seedPending(UUID.randomUUID(), "SHP-KR-TO", DOMESTIC));

        assertThat(result.getStatus()).isEqualTo(DispatchStatus.DISPATCH_FAILED);
    }

    @Test
    void repeated5xx_opensGoodsflowCircuit_withoutTrippingEasyPost() {
        GOODSFLOW.stubFor(post(urlPathEqualTo(SHIPMENTS))
                .willReturn(aResponse().withStatus(503)));

        for (int i = 0; i < 10; i++) {
            dispatchShipmentUseCase.dispatch(seedPending(UUID.randomUUID(), "SHP-KR-CB-" + i, DOMESTIC));
        }

        CircuitBreaker goodsflowCb = circuitBreakerRegistry.circuitBreaker("goodsflowDispatch");
        CircuitBreaker easyPostCb = circuitBreakerRegistry.circuitBreaker("easyPostDispatch");
        assertThat(goodsflowCb.getState())
                .isIn(CircuitBreaker.State.OPEN, CircuitBreaker.State.FORCED_OPEN);
        // I9 ISOLATION: the 굿스플로 outage must NOT open EasyPost's independent circuit.
        assertThat(easyPostCb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void idempotencyReplay_secondSendReturnsCachedAck_noSecondVendorCall() {
        GOODSFLOW.stubFor(post(urlPathEqualTo(SHIPMENTS))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(SUCCESS_BODY)));

        Dispatch seeded = seedPending(UUID.randomUUID(), "SHP-KR-DUP", DOMESTIC);

        DispatchAck first = goodsflowPort.dispatch(seeded);
        DispatchAck second = goodsflowPort.dispatch(seeded); // same shipmentId → dedupe

        assertThat(first.trackingNo()).isEqualTo("INV-1");
        assertThat(second.trackingNo()).isEqualTo("INV-1");
        // The dedupe short-circuit means 굿스플로 was called exactly once.
        GOODSFLOW.verify(exactly(1), postRequestedFor(urlPathEqualTo(SHIPMENTS)));
    }

    @Test
    void bulkheadFull_underConcurrency_rejectsSomeCalls() throws Exception {
        GOODSFLOW.stubFor(post(urlPathEqualTo(SHIPMENTS))
                .willReturn(aResponse().withStatus(200)
                        .withFixedDelay(800)
                        .withHeader("Content-Type", "application/json")
                        .withBody(SUCCESS_BODY)));

        int concurrency = 16;
        List<Dispatch> seeds = new ArrayList<>();
        for (int i = 0; i < concurrency; i++) {
            seeds.add(seedPending(UUID.randomUUID(), "SHP-KR-BH-" + i, DOMESTIC));
        }

        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        try {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (Dispatch seed : seeds) {
                tasks.add(() -> {
                    try {
                        goodsflowPort.dispatch(seed);
                        return true;
                    } catch (RuntimeException e) {
                        return false; // bulkhead full → ShipmentDispatchException
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
            assertThat(rejected).isGreaterThanOrEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
