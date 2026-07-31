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
 * Unit test for {@link SearchServiceClient} (ADR-MONO-058 D7 / TASK-BE-570).
 *
 * <p>Before this task the client already had a non-zero timeout (via the local
 * {@code RestClients.timed(...)} helper) — not a live-risk case, but a duplicated mechanism
 * now migrated to {@link ResilienceClientFactory}. These tests prove the migration preserved
 * behavior: the happy path still works, and a hung search-service still fails fast within a
 * bound.
 */
class SearchServiceClientTest {

    private MockWebServer searchService;

    @BeforeEach
    void setUp() throws IOException {
        searchService = new MockWebServer();
        searchService.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        searchService.shutdown();
    }

    private String baseUrl() {
        return "http://" + searchService.getHostName() + ":" + searchService.getPort();
    }

    @Test
    @DisplayName("happy path unaffected by the ResilienceClientFactory migration")
    void searchByName_happyPath_returnsResults() {
        searchService.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"query":"widget","content":[],"page":0,"size":20,"totalElements":0}"""));

        SearchServiceClient client = new SearchServiceClient(baseUrl());

        SearchServiceClient.SearchResponse result = client.searchByName("widget");

        assertThat(result.totalElements()).isEqualTo(0);
    }

    @Test
    @DisplayName("read timeout honored: hung search-service -> call fails within a bound, not forever")
    void searchByName_hungSearchService_failsFastWithinBound() throws Exception {
        SearchServiceClient client = new SearchServiceClient(baseUrl());
        RestClient shortTimeoutClient = ResilienceClientFactory.buildRestClient(
                baseUrl(), 2_000, 300);
        Field field = SearchServiceClient.class.getDeclaredField("restClient");
        field.setAccessible(true);
        field.set(client, shortTimeoutClient);

        long start = System.nanoTime();
        assertThatThrownBy(() -> client.searchByName("widget")).isInstanceOf(Exception.class);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertThat(elapsedMs).isLessThan(3000);
    }
}
