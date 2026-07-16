package com.wms.inventory.adapter.in.web.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.web.idempotency.IdempotencyFilterConfig;
import com.example.web.idempotency.IdempotencyKeyFilter;
import com.example.web.idempotency.JsonValueBodyCanonicalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.inventory.adapter.out.idempotency.InMemoryIdempotencyStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Regression coverage for TASK-BE-505 — inventory-service REST idempotency was
 * declared in the contract but never enforced (no filter consumed the
 * {@code IdempotencyStore}). These cases exercise the shared
 * {@link IdempotencyKeyFilter} wired with inventory-service's <em>production</em>
 * config (POST / {@code /api/v1/inventory/} / max-key 100), error writer, and
 * metrics — the mandated {@code idempotency.md} §6 REST scenarios that the
 * existing {@code AdjustmentTransferIntegrationTest} structurally cannot cover
 * (it drives {@code adjustStock.adjust(...)} at the application-service layer,
 * bypassing the filter, with a fresh random key per call).
 *
 * <p>Backed by the standalone-profile {@link InMemoryIdempotencyStore} so the
 * behavioural proof runs in the fast {@code test} lane, independent of the
 * Testcontainers Redis lane (a known flake on this host). The wiring proof —
 * that inventory's context actually registers this filter — lives in
 * {@code com.wms.inventory.config.IdempotencyConfigTest}.
 */
class InventoryIdempotencyFilterTest {

    private static final String PATH = "/api/v1/inventory/adjustments";
    private static final String BODY = "{\"inventoryId\":\"i-1\",\"bucket\":\"AVAILABLE\",\"delta\":-3,"
            + "\"reasonCode\":\"CYCLE_COUNT\",\"reasonNote\":\"count fix\"}";

    private ObjectMapper mapper;
    private SimpleMeterRegistry meterRegistry;
    private IdempotencyKeyFilter filter;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper().findAndRegisterModules();
        meterRegistry = new SimpleMeterRegistry();
        filter = new IdempotencyKeyFilter(
                new InMemoryIdempotencyStore(),
                new JsonValueBodyCanonicalizer(mapper),
                new InventoryIdempotencyErrorWriter(mapper, meterRegistry),
                new InventoryIdempotencyMetrics(meterRegistry),
                // Mirror of IdempotencyConfig's production filter config.
                IdempotencyFilterConfig.builder()
                        .methods("POST")
                        .pathPredicate(uri -> uri != null && uri.startsWith("/api/v1/inventory/"))
                        .maxKeyLength(100)
                        .lockTtl(Duration.ofSeconds(30))
                        .entryTtl(Duration.ofHours(24))
                        .build());
    }

    @Test
    @DisplayName("§6#2 first request with a new key executes once and caches the response")
    void firstRequest_executesAndCaches() throws Exception {
        AtomicInteger executions = new AtomicInteger();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(post("key-first", BODY), response, countingChain(executions, 201, "{\"id\":\"a1\"}"));

        assertThat(executions.get()).isEqualTo(1);
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(meterRegistry.counter("inventory.idempotency.lookup.count", "result", "miss").count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("§6#3 replay (same key, same body) returns the cached response and does NOT re-execute the mutation")
    void replaySameKeySameBody_notReExecuted() throws Exception {
        AtomicInteger executions = new AtomicInteger();

        // Round 1 — executes, caches the 201.
        filter.doFilter(post("key-replay", BODY), new MockHttpServletResponse(),
                countingChain(executions, 201, "{\"id\":\"first\"}"));

        // Round 2 — same key + same body → cached replay; chain MUST NOT run again.
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse round2 = new MockHttpServletResponse();
        filter.doFilter(post("key-replay", BODY), round2, chain);

        assertThat(executions.get())
                .as("the domain mutation must run exactly once across the retry")
                .isEqualTo(1);
        assertThat(round2.getStatus()).isEqualTo(201);
        assertThat(round2.getContentAsString()).isEqualTo("{\"id\":\"first\"}");
        verify(chain, never()).doFilter(any(), any());
        assertThat(meterRegistry.counter("inventory.idempotency.lookup.count", "result", "hit").count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("§6#4 same key + different body → 409 DUPLICATE_REQUEST + mismatch counter increments")
    void sameKeyDifferentBody_returns409_andCountsMismatch() throws Exception {
        AtomicInteger executions = new AtomicInteger();

        filter.doFilter(post("key-conflict", BODY), new MockHttpServletResponse(),
                countingChain(executions, 201, "{\"id\":\"first\"}"));

        String differentBody = BODY.replace("\"delta\":-3", "\"delta\":-99");
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse round2 = new MockHttpServletResponse();
        filter.doFilter(post("key-conflict", differentBody), round2, chain);

        assertThat(round2.getStatus()).isEqualTo(409);
        assertThat(round2.getContentAsString()).contains("DUPLICATE_REQUEST");
        assertThat(executions.get()).isEqualTo(1);
        verify(chain, never()).doFilter(any(), any());
        assertThat(meterRegistry.counter("inventory.idempotency.mismatch.count").count())
                .as("the spec's mismatch counter must fire only on body-mismatch 409")
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("§6#1 absent Idempotency-Key → filter skips (controller owns the 400)")
    void absentKey_filterSkips_chainInvoked() throws Exception {
        AtomicInteger executions = new AtomicInteger();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", PATH);
        request.setContentType("application/json");
        request.setContent(BODY.getBytes(StandardCharsets.UTF_8));

        filter.doFilter(request, new MockHttpServletResponse(), countingChain(executions, 201, "{}"));

        assertThat(executions.get())
                .as("filter must not short-circuit an absent key — the controller returns the 400")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("over-length key (> 100, the idempotency_key column bound) → 400 VALIDATION_ERROR before the handler")
    void overLengthKey_returns400() throws Exception {
        AtomicInteger executions = new AtomicInteger();
        String longKey = "k".repeat(101);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(post(longKey, BODY), response, countingChain(executions, 201, "{}"));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("VALIDATION_ERROR");
        assertThat(executions.get()).isZero();
    }

    @Test
    @DisplayName("different keys execute independently (no cross-key collision)")
    void differentKeys_independent() throws Exception {
        AtomicInteger executions = new AtomicInteger();
        filter.doFilter(post("key-a", BODY), new MockHttpServletResponse(),
                countingChain(executions, 201, "{}"));
        filter.doFilter(post("key-b", BODY), new MockHttpServletResponse(),
                countingChain(executions, 201, "{}"));
        assertThat(executions.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("GET and non-inventory paths are not guarded (config boundary)")
    void nonGuardedRequests_skip() throws Exception {
        AtomicInteger executions = new AtomicInteger();

        // GET on the inventory path — not a mutating method.
        MockHttpServletRequest get = new MockHttpServletRequest("GET", PATH);
        get.addHeader("Idempotency-Key", "key-get");
        filter.doFilter(get, new MockHttpServletResponse(), countingChain(executions, 200, "{}"));

        // POST outside the inventory prefix — e.g. actuator.
        MockHttpServletRequest other = new MockHttpServletRequest("POST", "/actuator/refresh");
        other.addHeader("Idempotency-Key", "key-get");
        other.setContent(BODY.getBytes(StandardCharsets.UTF_8));
        filter.doFilter(other, new MockHttpServletResponse(), countingChain(executions, 200, "{}"));

        // Same key would have collided if guarded; both pass through independently.
        assertThat(executions.get()).isEqualTo(2);
    }

    // ------------------------------------------------------------------ helpers

    private static MockHttpServletRequest post(String idemKey, String body) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", PATH);
        req.setContentType("application/json");
        req.addHeader("Idempotency-Key", idemKey);
        req.setContent(body.getBytes(StandardCharsets.UTF_8));
        return req;
    }

    private static FilterChain countingChain(AtomicInteger counter, int status, String body) {
        return (req, res) -> {
            counter.incrementAndGet();
            HttpServletResponse httpResp = (HttpServletResponse) res;
            httpResp.setStatus(status);
            httpResp.setContentType("application/json");
            httpResp.getWriter().write(body);
        };
    }
}
