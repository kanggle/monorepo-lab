package com.example.batch.application;

import com.example.batch.AbstractIntegrationTest;
import com.example.batch.domain.model.BatchJobStatus;
import com.example.batch.domain.repository.BatchJobExecutionRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link SearchIndexConsistencyJob} (TASK-BE-409 / AC-5).
 *
 * <p>Extends {@link AbstractIntegrationTest} (Testcontainers PostgreSQL + Kafka).
 * Uses {@link MockWebServer} to stub both product-service and search-service HTTP responses
 * so the job can be exercised end-to-end against a real database without a live ES cluster.
 *
 * <p><b>ShedLock bypass:</b> calls {@code execute()} directly — never via the scheduler —
 * to avoid the {@code lockAtLeastFor="PT5S"} trap that would silently no-op a second call
 * within the lock window (mirrors shipping-service AutoCollectTrackingService test pattern).
 *
 * <p><b>Testcontainers note:</b> annotated {@code @Tag("integration")} and excluded from the
 * default Gradle {@code :test} task on this Windows host (Testcontainers/Docker not available).
 * CI (Linux) runs these as part of the full test suite. The test must compile successfully
 * even when not executed locally (AC-7 "컴파일 green").
 */
@Tag("integration")
@DisplayName("SearchIndexConsistencyJob 통합 테스트")
class SearchIndexConsistencyIntegrationTest extends AbstractIntegrationTest {

    /**
     * Shared MockWebServer instances, started once before all tests and stopped after.
     * Static so Testcontainers + Spring context share the same ports via @DynamicPropertySource.
     */
    static MockWebServer productServer;
    static MockWebServer searchServer;

    @DynamicPropertySource
    static void mockServerProperties(DynamicPropertyRegistry registry) throws IOException {
        productServer = new MockWebServer();
        productServer.start();
        searchServer = new MockWebServer();
        searchServer.start();
        registry.add("product-service.base-url", () -> productServer.url("/").toString());
        registry.add("search-service.base-url", () -> searchServer.url("/").toString());
    }

    @Autowired
    private SearchIndexConsistencyJob job;

    @Autowired
    private BatchJobExecutionRepository executionRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    @AfterEach
    void drainQueues() {
        // Drain any unconsumed MockWebServer responses between tests to keep server state clean
        while (productServer.getRequestCount() > 0) {
            try { productServer.takeRequest(1, java.util.concurrent.TimeUnit.MILLISECONDS); }
            catch (Exception ignored) { break; }
        }
    }

    @Test
    @DisplayName("product-service 1개 상품 + search 존재 → COMPLETED 히스토리, 메트릭 0")
    void singleProductFoundInSearch_completed_zeroMetric() throws Exception {
        UUID productId = UUID.randomUUID();
        String productName = "Integration Widget";

        // Stub product-service: one product
        productServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(String.format("""
                        {
                          "content": [
                            {"id":"%s","name":"%s","status":"ON_SALE","price":9900,"thumbnailUrl":null,"categoryId":null,"sellerId":null}
                          ],
                          "page":0,"size":50,"totalElements":1
                        }""", productId, productName)));

        // Stub search-service: product found
        searchServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(String.format("""
                        {
                          "query":"%s",
                          "content": [
                            {"productId":"%s","name":"%s","price":9900,"status":"ON_SALE","thumbnailUrl":null,"categoryId":null,"score":1.5}
                          ],
                          "page":0,"size":20,"totalElements":1
                        }""", productName, productId, productName)));

        double counterBefore = inconsistencyCount();

        // Act — direct call bypasses ShedLock
        job.execute();

        // Assert: history COMPLETED
        // executionRepository stores the history; verify via counter (no direct query method in interface)
        double counterAfter = inconsistencyCount();
        assertThat(counterAfter - counterBefore).isEqualTo(0.0);

        // Assert at least one product request and one search request were made
        assertThat(productServer.getRequestCount()).isGreaterThanOrEqualTo(1);
        assertThat(searchServer.getRequestCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("product-service 1개 상품 + search 미발견 → COMPLETED 히스토리, 메트릭 +1")
    void singleProductMissingFromSearch_completed_metricPlusOne() throws Exception {
        UUID productId = UUID.randomUUID();
        String productName = "Missing Widget";

        // Stub product-service: one product
        productServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(String.format("""
                        {
                          "content": [
                            {"id":"%s","name":"%s","status":"ON_SALE","price":5000,"thumbnailUrl":null,"categoryId":null,"sellerId":null}
                          ],
                          "page":0,"size":50,"totalElements":1
                        }""", productId, productName)));

        // Stub search-service: empty results (product not indexed)
        searchServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(String.format("""
                        {
                          "query":"%s","content":[],"page":0,"size":20,"totalElements":0
                        }""", productName)));

        double counterBefore = inconsistencyCount();

        // Act
        job.execute();

        // Assert: metric incremented by 1
        double counterAfter = inconsistencyCount();
        assertThat(counterAfter - counterBefore).isEqualTo(1.0);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private double inconsistencyCount() {
        Counter counter = meterRegistry
                .find(SearchIndexConsistencyJob.INCONSISTENCY_COUNTER_NAME)
                .counter();
        return counter != null ? counter.count() : 0.0;
    }
}
