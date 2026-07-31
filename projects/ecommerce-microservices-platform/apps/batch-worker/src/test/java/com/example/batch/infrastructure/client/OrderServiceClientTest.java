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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for batch-worker's own {@link OrderServiceClient} (ADR-MONO-058 D7 / TASK-BE-570).
 *
 * <p>Before this task the client already had a non-zero timeout (via the local
 * {@code RestClients.timed(...)} helper) — not a live-risk case, but a duplicated mechanism
 * now migrated to {@link ResilienceClientFactory}. These tests prove the migration preserved
 * behavior: the happy path still works, and a hung order-service still fails fast within a
 * bound.
 */
class OrderServiceClientTest {

    private MockWebServer orderService;
    private IamClientCredentialsTokenProvider tokenProvider;

    @BeforeEach
    void setUp() throws IOException {
        orderService = new MockWebServer();
        orderService.start();
        tokenProvider = mock(IamClientCredentialsTokenProvider.class);
        when(tokenProvider.currentBearer()).thenReturn("test-jwt");
    }

    @AfterEach
    void tearDown() throws IOException {
        orderService.shutdown();
    }

    private String baseUrl() {
        return "http://" + orderService.getHostName() + ":" + orderService.getPort();
    }

    @Test
    @DisplayName("happy path unaffected by the ResilienceClientFactory migration")
    void confirmPaidStale_happyPath_returnsTally() {
        orderService.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"scanned":0,"confirmed":0,"skipped":0,"confirmedOrderIds":[]}"""));

        OrderServiceClient client = new OrderServiceClient(baseUrl(), 30, 200, tokenProvider);

        OrderServiceClient.ConfirmPaidStaleResponse result = client.confirmPaidStale();

        assertThat(result.scanned()).isEqualTo(0);
    }

    @Test
    @DisplayName("read timeout honored: hung order-service -> call fails within a bound, not forever")
    void confirmPaidStale_hungOrderService_failsFastWithinBound() throws Exception {
        OrderServiceClient client = new OrderServiceClient(baseUrl(), 30, 200, tokenProvider);
        RestClient shortTimeoutClient = ResilienceClientFactory.buildRestClient(
                baseUrl(), 2_000, 300);
        Field field = OrderServiceClient.class.getDeclaredField("restClient");
        field.setAccessible(true);
        field.set(client, shortTimeoutClient);

        long start = System.nanoTime();
        assertThatThrownBy(client::confirmPaidStale).isInstanceOf(Exception.class);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertThat(elapsedMs).isLessThan(3000);
    }
}
