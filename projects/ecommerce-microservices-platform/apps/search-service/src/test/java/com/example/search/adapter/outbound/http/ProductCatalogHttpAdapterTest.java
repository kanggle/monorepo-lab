package com.example.search.adapter.outbound.http;

import com.example.search.domain.model.SearchDocument;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test for {@link ProductCatalogHttpAdapter} (ADR-MONO-058 D7 / TASK-BE-570).
 *
 * <p>Before this task the adapter's {@code RestClient} had NO request factory at all — a bare
 * {@code RestClient.builder()} — meaning zero connect/read timeout. A hung product-service call
 * blocked the reindex thread indefinitely: the live production-risk gap this task closes. These
 * tests pin: (1) the happy path still works after the {@code ResilienceClientFactory} migration,
 * and (2) a hung product-service response now fails FAST within the configured read timeout
 * instead of hanging forever, using a real {@link MockWebServer} (not a mocked transport) so the
 * timeout is genuinely exercised at the socket level.
 */
class ProductCatalogHttpAdapterTest {

    private MockWebServer productService;

    @BeforeEach
    void setUp() throws IOException {
        productService = new MockWebServer();
        productService.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        productService.shutdown();
    }

    private String baseUrl() {
        return "http://" + productService.getHostName() + ":" + productService.getPort();
    }

    @Test
    @DisplayName("happy path unaffected by the ResilienceClientFactory migration")
    void fetchAll_happyPath_returnsDocuments() {
        productService.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"content":[{"id":"p-1"}],"totalPages":1}"""));
        productService.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"id":"p-1","name":"Widget","description":"d","price":1000,
                         "status":"ON_SALE","categoryId":"c-1","thumbnailUrl":null,"variants":[]}"""));

        ProductCatalogHttpAdapter adapter = new ProductCatalogHttpAdapter(baseUrl(), 2000, 5000);

        List<SearchDocument> result = adapter.fetchAll(10);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("read timeout honored: hung product-service list call -> fetchAll fails within the configured bound, not forever")
    void fetchAll_listCallHangsBeyondReadTimeout_failsFastWithinBound() {
        // No response enqueued: MockWebServer accepts the connection but never writes a
        // response body, simulating a hung downstream call.
        ProductCatalogHttpAdapter adapter = new ProductCatalogHttpAdapter(baseUrl(), 2000, 300);

        long start = System.nanoTime();
        assertThatThrownBy(() -> adapter.fetchAll(10)).isInstanceOf(Exception.class);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        // Generous upper bound (10x the configured 300ms read timeout) to absorb CI scheduling
        // jitter while still proving the call did NOT hang indefinitely.
        assertThat(elapsedMs).isLessThan(3000);
    }
}
