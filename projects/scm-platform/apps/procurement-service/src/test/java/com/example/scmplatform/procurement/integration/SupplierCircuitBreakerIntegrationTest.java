package com.example.scmplatform.procurement.integration;

import com.example.scmplatform.procurement.application.ActorContext;
import com.example.scmplatform.procurement.application.PurchaseOrderApplicationService;
import com.example.scmplatform.procurement.application.command.SubmitPurchaseOrderCommand;
import com.example.scmplatform.procurement.domain.error.SupplierUnavailableException;
import com.example.scmplatform.procurement.domain.po.PurchaseOrder;
import com.example.scmplatform.procurement.domain.supplier.Supplier;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * IT-3: Supplier circuit breaker.
 *
 * <p>Configuration from application.yml:
 * <ul>
 *   <li>{@code minimumNumberOfCalls=5} — circuit evaluates after 5 calls.</li>
 *   <li>{@code failureRateThreshold=50} — 50 % failure → OPEN.</li>
 *   <li>{@code slidingWindowSize=10} (TIME_BASED, 10 s window).</li>
 * </ul>
 *
 * <p>Strategy: override to a COUNT_BASED window of size 3 with 100 %
 * failure threshold so a single test can reliably open the circuit with
 * exactly 3 stub 5xx responses.
 *
 * <p>The test class resets the circuit breaker to CLOSED before each run to
 * avoid cross-test leakage.
 */
@Tag("integration")
@DisplayName("IT-3: Supplier circuit breaker opens after 5xx failures")
@TestPropertySource(properties = {
        "resilience4j.circuitbreaker.instances.supplier.sliding-window-type=COUNT_BASED",
        "resilience4j.circuitbreaker.instances.supplier.sliding-window-size=3",
        "resilience4j.circuitbreaker.instances.supplier.minimum-number-of-calls=3",
        "resilience4j.circuitbreaker.instances.supplier.failure-rate-threshold=100",
        "resilience4j.circuitbreaker.instances.supplier.wait-duration-in-open-state=60s",
        // Disable retry so each attempt hits MockWebServer directly
        "resilience4j.retry.instances.supplier.max-attempts=1"
})
class SupplierCircuitBreakerIntegrationTest extends AbstractProcurementIntegrationTest {

    private static MockWebServer supplierMock;

    @DynamicPropertySource
    static void supplierMockUrl(DynamicPropertyRegistry registry) throws IOException {
        supplierMock = new MockWebServer();
        supplierMock.start();
        registry.add("scmplatform.procurement.supplier.mock.base-url",
                () -> "http://" + supplierMock.getHostName() + ":" + supplierMock.getPort());
    }

    @Autowired
    private PurchaseOrderApplicationService service;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void resetCircuitBreaker() {
        // Reset to CLOSED before each test to avoid state leakage.
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("supplier");
        cb.reset();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (supplierMock != null) {
            supplierMock.shutdown();
        }
    }

    @Test
    @DisplayName("공급사 5xx 3회 → circuit OPEN → SupplierUnavailableException → 503")
    void threeSupplierFailuresOpenCircuit() throws InterruptedException {
        Supplier supplier = persistActiveSupplier(TENANT_SCM);
        ActorContext buyer = new ActorContext("buyer-cb-001", TENANT_SCM, Set.of("BUYER"));

        // Enqueue 3 server errors to trip the circuit
        for (int i = 0; i < 3; i++) {
            supplierMock.enqueue(new MockResponse().setResponseCode(503)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"error\":\"service unavailable\"}"));
        }

        // First 3 calls exhaust the circuit window (min-calls=3, failure-rate=100%).
        for (int i = 0; i < 3; i++) {
            PurchaseOrder po = persistDraftPo(TENANT_SCM, supplier.getId());
            try {
                service.submit(new SubmitPurchaseOrderCommand(buyer, po.getId(),
                        "idem-cb-" + i));
            } catch (SupplierUnavailableException | RuntimeException ignored) {
                // Expected — each call fails; after 3rd the circuit opens.
            }
        }

        // Assert circuit is OPEN now
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("supplier");
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Next call should be rejected by the OPEN circuit → SupplierUnavailableException.
        PurchaseOrder poAfterOpen = persistDraftPo(TENANT_SCM, supplier.getId());
        assertThatThrownBy(() ->
                service.submit(new SubmitPurchaseOrderCommand(buyer, poAfterOpen.getId(),
                        "idem-cb-open")))
                .isInstanceOf(SupplierUnavailableException.class)
                .hasMessageContaining("circuit OPEN");
    }
}
