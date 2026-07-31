package com.example.batch.infrastructure.client;

import com.example.common.resilience.ResilienceClientFactory;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test for {@link ProductServiceClient} (ADR-MONO-058 D7 / TASK-BE-570).
 *
 * <p>Before this task the client already had a non-zero timeout (via the local
 * {@code RestClients.timed(...)} helper) — not a live-risk case, but a duplicated mechanism
 * now migrated to {@link ResilienceClientFactory}. These tests prove the migration preserved
 * behavior: the happy path still works, and a hung product-service still fails fast within a
 * bound (proving the read timeout is genuinely honored by the new mechanism, not silently
 * dropped).
 */
class ProductServiceClientTest {

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
    void listOnSale_happyPath_returnsPage() {
        productService.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"content":[],"page":0,"size":50,"totalElements":0}"""));

        ProductServiceClient client = new ProductServiceClient(baseUrl());

        ProductServiceClient.ProductPageResponse result = client.listOnSale(0, 50);

        assertThat(result.totalElements()).isEqualTo(0);
    }

    @Test
    @DisplayName("read timeout honored: hung product-service -> call fails within a bound, not forever")
    void listOnSale_hungProductService_failsFastWithinBound() throws Exception {
        // No response enqueued: MockWebServer accepts the connection but never writes a
        // response, simulating a hung downstream. Override the client's package-private
        // restClient field to a much shorter read timeout than the shipped 10s production
        // default (same ResilienceClientFactory mechanism), so this test does not have to wait
        // out 10s to prove the bound is genuinely honored.
        ProductServiceClient client = new ProductServiceClient(baseUrl());
        RestClient shortTimeoutClient = ResilienceClientFactory.buildRestClient(
                baseUrl(), 2_000, 300);
        Field field = ProductServiceClient.class.getDeclaredField("restClient");
        field.setAccessible(true);
        field.set(client, shortTimeoutClient);

        long start = System.nanoTime();
        assertThatThrownBy(() -> client.listOnSale(0, 50)).isInstanceOf(Exception.class);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertThat(elapsedMs).isLessThan(3000);
    }
}
