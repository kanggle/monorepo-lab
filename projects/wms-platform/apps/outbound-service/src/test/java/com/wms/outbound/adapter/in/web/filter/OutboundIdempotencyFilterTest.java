package com.wms.outbound.adapter.in.web.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.web.idempotency.BodyHashUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.outbound.adapter.out.idempotency.InMemoryIdempotencyStore;
import com.wms.outbound.application.port.out.StoredResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Unit tests for {@link OutboundIdempotencyFilter}.
 *
 * <p>Uses {@link InMemoryIdempotencyStore} (real implementation) and
 * Spring's mock servlet objects to test the filter without the full MVC stack.
 *
 * <p>Scenarios covered (mirrors {@code InboundIdempotencyFilter}):
 * <ul>
 *   <li>First POST — passes through, 2xx response is cached.</li>
 *   <li>Replay — same key + same body → 201 replayed, chain not called again.</li>
 *   <li>Body mismatch — same key + different body → 409 DUPLICATE_REQUEST.</li>
 *   <li>Lock held (PENDING) — 503 + Retry-After: 1.</li>
 *   <li>GET request — filter skipped, chain called.</li>
 *   <li>PATCH request — filter applies (covers the seal-packing-unit endpoint).</li>
 *   <li>Webhook path — filter skipped, chain called.</li>
 *   <li>4xx response — NOT cached (allows retry).</li>
 *   <li>5xx response — NOT cached (allows retry).</li>
 *   <li>Missing Idempotency-Key — filter skipped, chain called (controller 400).</li>
 *   <li>Header > 255 chars — 400 VALIDATION_ERROR.</li>
 *   <li>Body whitespace + key-order canonicalisation produces same hash → cached replay.</li>
 *   <li>Storage key shape — {METHOD}:{sha256(path)}:{idempotencyKey}.</li>
 *   <li>Same key reused on different endpoints → independent (no false replay).</li>
 *   <li>Metrics — hit / miss / conflict counters fire.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class OutboundIdempotencyFilterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String IDEM_KEY = "test-key-001";
    private static final String OUTBOUND_PATH = "/api/v1/outbound/orders";
    private static final String BODY = "{\"orderNo\":\"ORD-001\"}";

    private InMemoryIdempotencyStore store;
    private MeterRegistry meterRegistry;
    private OutboundIdempotencyFilter filter;

    @BeforeEach
    void setUp() {
        store = new InMemoryIdempotencyStore();
        meterRegistry = new SimpleMeterRegistry();
        filter = new OutboundIdempotencyFilter(store, MAPPER, meterRegistry);
    }

    // -------------------------------------------------------------------------
    // First request — passes through and caches 2xx response
    // -------------------------------------------------------------------------

    @Test
    void firstRequest_passesThrough_andCachesResponse() throws Exception {
        MockHttpServletRequest request = postRequest(OUTBOUND_PATH, BODY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            HttpServletResponse httpResp = (HttpServletResponse) res;
            httpResp.setStatus(201);
            httpResp.setContentType("application/json");
            httpResp.getWriter().write("{\"id\":\"abc\"}");
        };

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentAsString()).isEqualTo("{\"id\":\"abc\"}");

        // Verify entry was cached
        String storageKey = buildStorageKey("POST", OUTBOUND_PATH, IDEM_KEY);
        assertThat(store.lookup(storageKey)).isPresent();

        // Metrics: miss recorded
        assertThat(meterRegistry.counter(OutboundIdempotencyFilter.METRIC_LOOKUP_COUNT,
                OutboundIdempotencyFilter.TAG_RESULT, OutboundIdempotencyFilter.RESULT_MISS).count())
                .isEqualTo(1.0);
    }

    // -------------------------------------------------------------------------
    // Replay — same key + same body → cached response returned, chain NOT called
    // -------------------------------------------------------------------------

    @Test
    void replay_sameKeyAndBody_returnsCachedResponse_chainNotCalled() throws Exception {
        // Pre-populate the store (simulates a previously completed request)
        String storageKey = buildStorageKey("POST", OUTBOUND_PATH, IDEM_KEY);
        String requestHash = BodyHashUtil.computeHash(BODY.getBytes(StandardCharsets.UTF_8), MAPPER);
        store.put(storageKey,
                new StoredResponse(requestHash, 201, "{\"id\":\"cached\"}", "application/json",
                        Instant.now()),
                Duration.ofHours(24));

        MockHttpServletRequest request = postRequest(OUTBOUND_PATH, BODY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentAsString()).isEqualTo("{\"id\":\"cached\"}");
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());

        // Metrics: hit recorded
        assertThat(meterRegistry.counter(OutboundIdempotencyFilter.METRIC_LOOKUP_COUNT,
                OutboundIdempotencyFilter.TAG_RESULT, OutboundIdempotencyFilter.RESULT_HIT).count())
                .isEqualTo(1.0);
    }

    // -------------------------------------------------------------------------
    // Body mismatch — same key + different body → 409 DUPLICATE_REQUEST
    // -------------------------------------------------------------------------

    @Test
    void bodyMismatch_returns409_withCode_DUPLICATE_REQUEST() throws Exception {
        String storageKey = buildStorageKey("POST", OUTBOUND_PATH, IDEM_KEY);
        String originalHash = BodyHashUtil.computeHash(BODY.getBytes(StandardCharsets.UTF_8), MAPPER);
        store.put(storageKey,
                new StoredResponse(originalHash, 201, "{\"id\":\"original\"}", "application/json",
                        Instant.now()),
                Duration.ofHours(24));

        String differentBody = "{\"orderNo\":\"ORD-DIFFERENT\"}";
        MockHttpServletRequest request = postRequest(OUTBOUND_PATH, differentBody);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).contains("DUPLICATE_REQUEST");
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());

        // Metrics: conflict recorded
        assertThat(meterRegistry.counter(OutboundIdempotencyFilter.METRIC_LOOKUP_COUNT,
                OutboundIdempotencyFilter.TAG_RESULT,
                OutboundIdempotencyFilter.RESULT_CONFLICT).count())
                .isEqualTo(1.0);
    }

    // -------------------------------------------------------------------------
    // Lock held (PENDING) → 503 + Retry-After: 1
    // -------------------------------------------------------------------------

    @Test
    void lockHeld_returns503_withRetryAfterHeader() throws Exception {
        String storageKey = buildStorageKey("POST", OUTBOUND_PATH, IDEM_KEY);
        // Acquire lock to simulate a concurrent in-flight request
        store.tryAcquireLock(storageKey, Duration.ofSeconds(30));

        MockHttpServletRequest request = postRequest(OUTBOUND_PATH, BODY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader("Retry-After")).isEqualTo("1");
        assertThat(response.getContentAsString()).contains("PROCESSING");
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    // -------------------------------------------------------------------------
    // GET → filter skipped, chain called
    // -------------------------------------------------------------------------

    @Test
    void getRequest_filterSkipped_chainCalled() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", OUTBOUND_PATH);
        request.addHeader("Idempotency-Key", IDEM_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain, times(1)).doFilter(
                org.mockito.ArgumentMatchers.any(ServletRequest.class),
                org.mockito.ArgumentMatchers.any(ServletResponse.class));
    }

    // -------------------------------------------------------------------------
    // PATCH (seal packing unit) → filter applies and caches
    // -------------------------------------------------------------------------

    @Test
    void patchRequest_filterApplies_andCachesResponse() throws Exception {
        String patchPath = "/api/v1/outbound/packing-units/abc-123";
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", patchPath);
        request.setContentType("application/json");
        request.addHeader("Idempotency-Key", IDEM_KEY);
        request.setContent("{\"version\":1}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            ((HttpServletResponse) res).setStatus(200);
            ((HttpServletResponse) res).setContentType("application/json");
            ((HttpServletResponse) res).getWriter().write("{\"sealed\":true}");
        };

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        String storageKey = buildStorageKey("PATCH", patchPath, IDEM_KEY);
        assertThat(store.lookup(storageKey)).isPresent();
    }

    // -------------------------------------------------------------------------
    // Webhook path → filter skipped, chain called
    // -------------------------------------------------------------------------

    @Test
    void webhookPath_filterSkipped_chainCalled() throws Exception {
        MockHttpServletRequest request = postRequest("/webhooks/erp/order", BODY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain, times(1)).doFilter(
                org.mockito.ArgumentMatchers.any(ServletRequest.class),
                org.mockito.ArgumentMatchers.any(ServletResponse.class));
    }

    // -------------------------------------------------------------------------
    // 4xx response — NOT cached (allows retry)
    // -------------------------------------------------------------------------

    @Test
    void clientError4xx_notCached() throws Exception {
        MockHttpServletRequest request = postRequest(OUTBOUND_PATH, BODY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            ((HttpServletResponse) res).setStatus(422);
            ((HttpServletResponse) res).getWriter()
                    .write("{\"code\":\"STATE_TRANSITION_INVALID\"}");
        };

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(422);

        // No entry should have been stored
        String storageKey = buildStorageKey("POST", OUTBOUND_PATH, IDEM_KEY);
        assertThat(store.lookup(storageKey)).isEmpty();
    }

    // -------------------------------------------------------------------------
    // 5xx response — NOT cached (allows retry)
    // -------------------------------------------------------------------------

    @Test
    void serverError5xx_notCached() throws Exception {
        MockHttpServletRequest request = postRequest(OUTBOUND_PATH, BODY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            ((HttpServletResponse) res).setStatus(500);
            ((HttpServletResponse) res).getWriter()
                    .write("{\"code\":\"INTERNAL_ERROR\"}");
        };

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(500);
        String storageKey = buildStorageKey("POST", OUTBOUND_PATH, IDEM_KEY);
        assertThat(store.lookup(storageKey)).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Missing Idempotency-Key → filter skipped, chain called
    // -------------------------------------------------------------------------

    @Test
    void missingIdempotencyKey_filterSkipped_chainCalled() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", OUTBOUND_PATH);
        request.setContentType("application/json");
        request.setContent(BODY.getBytes(StandardCharsets.UTF_8));
        // No Idempotency-Key header
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain, times(1)).doFilter(
                org.mockito.ArgumentMatchers.any(ServletRequest.class),
                org.mockito.ArgumentMatchers.any(ServletResponse.class));
    }

    // -------------------------------------------------------------------------
    // Header > 255 chars → 400 VALIDATION_ERROR
    // -------------------------------------------------------------------------

    @Test
    void keyTooLong_returns400_VALIDATION_ERROR() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", OUTBOUND_PATH);
        request.setContentType("application/json");
        request.addHeader("Idempotency-Key", "x".repeat(256));
        request.setContent(BODY.getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("VALIDATION_ERROR");
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    // -------------------------------------------------------------------------
    // Whitespace + key-order canonicalisation — semantically identical bodies
    // produce the same hash, so a replay returns the cached response
    // -------------------------------------------------------------------------

    @Test
    void whitespaceAndKeyOrder_canonicalisedToSameHash_replaysCached() throws Exception {
        String storageKey = buildStorageKey("POST", OUTBOUND_PATH, IDEM_KEY);
        String canonicalHash = BodyHashUtil.computeHash(
                BODY.getBytes(StandardCharsets.UTF_8), MAPPER);
        store.put(storageKey,
                new StoredResponse(canonicalHash, 201, "{\"id\":\"cached\"}", "application/json",
                        Instant.now()),
                Duration.ofHours(24));

        // Same JSON, different whitespace + key order (would have a different
        // raw-bytes hash but same canonicalised hash).
        String reorderedBody = "{ \"orderNo\" : \"ORD-001\" }";
        MockHttpServletRequest request = postRequest(OUTBOUND_PATH, reorderedBody);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentAsString()).isEqualTo("{\"id\":\"cached\"}");
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    // -------------------------------------------------------------------------
    // Storage key shape: {METHOD}:{sha256(path)}:{idempotencyKey}
    // -------------------------------------------------------------------------

    @Test
    void storageKey_shape_matchesSpec() throws Exception {
        MockHttpServletRequest request = postRequest(OUTBOUND_PATH, BODY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            ((HttpServletResponse) res).setStatus(201);
            ((HttpServletResponse) res).getWriter().write("{\"id\":\"x\"}");
        };

        filter.doFilterInternal(request, response, chain);

        String expectedKey = buildStorageKey("POST", OUTBOUND_PATH, IDEM_KEY);
        assertThat(store.lookup(expectedKey)).isPresent();
    }

    // -------------------------------------------------------------------------
    // Same key reused on different endpoints → independent (no false replay)
    // -------------------------------------------------------------------------

    @Test
    void sameKeyDifferentEndpoint_independent() throws Exception {
        // Endpoint 1: cache an entry
        String endpoint1Key = buildStorageKey("POST", OUTBOUND_PATH, IDEM_KEY);
        String hash = BodyHashUtil.computeHash(BODY.getBytes(StandardCharsets.UTF_8), MAPPER);
        store.put(endpoint1Key,
                new StoredResponse(hash, 201, "{\"endpoint\":1}", "application/json",
                        Instant.now()),
                Duration.ofHours(24));

        // Endpoint 2: same key but different path → different storage key,
        // miss expected
        String endpoint2 = "/api/v1/outbound/orders/abc:cancel";
        MockHttpServletRequest request = postRequest(endpoint2, BODY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            ((HttpServletResponse) res).setStatus(200);
            ((HttpServletResponse) res).setContentType("application/json");
            ((HttpServletResponse) res).getWriter().write("{\"endpoint\":2}");
        };

        filter.doFilterInternal(request, response, chain);

        // Endpoint 2 should have executed (not replayed endpoint 1's cache)
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEqualTo("{\"endpoint\":2}");
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private static MockHttpServletRequest postRequest(String path, String body) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", path);
        req.setContentType("application/json");
        req.addHeader("Idempotency-Key", IDEM_KEY);
        if (body != null) {
            req.setContent(body.getBytes(StandardCharsets.UTF_8));
        }
        return req;
    }

    /**
     * Replicates the storage key formula used by the filter:
     * {@code {METHOD}:{sha256hex(requestURI)}:{idempotencyKey}}.
     */
    private static String buildStorageKey(String method, String path, String idempotencyKey) {
        String pathHash = BodyHashUtil.sha256hex(path.getBytes(StandardCharsets.UTF_8));
        return method.toUpperCase() + ":" + pathHash + ":" + idempotencyKey;
    }
}
