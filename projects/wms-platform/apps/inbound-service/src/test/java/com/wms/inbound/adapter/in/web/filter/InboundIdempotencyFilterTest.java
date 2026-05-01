package com.wms.inbound.adapter.in.web.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.inbound.adapter.out.idempotency.InMemoryIdempotencyStore;
import com.wms.inbound.application.port.out.StoredResponse;
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
 * Unit tests for {@link InboundIdempotencyFilter}.
 *
 * <p>Uses {@link InMemoryIdempotencyStore} (real implementation) and
 * Spring's mock servlet objects to test the filter without the full MVC stack.
 *
 * <p>Scenarios covered:
 * <ul>
 *   <li>First request — passes through, 2xx response is cached.</li>
 *   <li>Replay — same key + same body → 201 replayed, chain not called again.</li>
 *   <li>Body mismatch — same key + different body → 409 DUPLICATE_REQUEST.</li>
 *   <li>Lock held (PENDING) — 503 + Retry-After: 1.</li>
 *   <li>Non-POST — filter skipped, chain called.</li>
 *   <li>Webhook path — filter skipped, chain called.</li>
 *   <li>4xx response — NOT cached (allows retry).</li>
 *   <li>Missing Idempotency-Key — filter skipped, chain called.</li>
 *   <li>Storage key shape — POST:{sha256(path)}:{idempotencyKey}.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class InboundIdempotencyFilterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String IDEM_KEY = "test-key-001";
    private static final String INBOUND_PATH = "/api/v1/inbound/asns";
    private static final String BODY = "{\"asnNo\":\"ASN-001\"}";

    private InMemoryIdempotencyStore store;
    private InboundIdempotencyFilter filter;

    @BeforeEach
    void setUp() {
        store = new InMemoryIdempotencyStore();
        filter = new InboundIdempotencyFilter(store, MAPPER);
    }

    // -------------------------------------------------------------------------
    // First request — passes through and caches 2xx response
    // -------------------------------------------------------------------------

    @Test
    void firstRequest_passesThrough_andCachesResponse() throws Exception {
        MockHttpServletRequest request = postRequest(INBOUND_PATH, BODY);
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
        String storageKey = buildStorageKey(INBOUND_PATH, IDEM_KEY);
        assertThat(store.lookup(storageKey)).isPresent();
    }

    // -------------------------------------------------------------------------
    // Replay — same key + same body → cached response returned, chain NOT called
    // -------------------------------------------------------------------------

    @Test
    void replay_sameKeyAndBody_returnsCachedResponse_chainNotCalled() throws Exception {
        // Pre-populate the store (simulates a previously completed request)
        String storageKey = buildStorageKey(INBOUND_PATH, IDEM_KEY);
        String requestHash = BodyHashUtil.computeHash(BODY.getBytes(StandardCharsets.UTF_8), MAPPER);
        store.put(storageKey,
                new StoredResponse(requestHash, 201, "{\"id\":\"cached\"}", "application/json",
                        Instant.now()),
                Duration.ofHours(24));

        MockHttpServletRequest request = postRequest(INBOUND_PATH, BODY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentAsString()).isEqualTo("{\"id\":\"cached\"}");
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    // -------------------------------------------------------------------------
    // Body mismatch — same key + different body → 409 DUPLICATE_REQUEST
    // -------------------------------------------------------------------------

    @Test
    void bodyMismatch_returns409_withCode_DUPLICATE_REQUEST() throws Exception {
        String storageKey = buildStorageKey(INBOUND_PATH, IDEM_KEY);
        String originalHash = BodyHashUtil.computeHash(BODY.getBytes(StandardCharsets.UTF_8), MAPPER);
        store.put(storageKey,
                new StoredResponse(originalHash, 201, "{\"id\":\"original\"}", "application/json",
                        Instant.now()),
                Duration.ofHours(24));

        String differentBody = "{\"asnNo\":\"ASN-DIFFERENT\"}";
        MockHttpServletRequest request = postRequest(INBOUND_PATH, differentBody);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).contains("DUPLICATE_REQUEST");
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    // -------------------------------------------------------------------------
    // Lock held (PENDING) → 503 + Retry-After: 1
    // -------------------------------------------------------------------------

    @Test
    void lockHeld_returns503_withRetryAfterHeader() throws Exception {
        String storageKey = buildStorageKey(INBOUND_PATH, IDEM_KEY);
        // Acquire lock to simulate a concurrent in-flight request
        store.tryAcquireLock(storageKey, Duration.ofSeconds(30));

        MockHttpServletRequest request = postRequest(INBOUND_PATH, BODY);
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
    // Non-POST → filter skipped, chain called
    // -------------------------------------------------------------------------

    @Test
    void getRequest_filterSkipped_chainCalled() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", INBOUND_PATH);
        request.addHeader("Idempotency-Key", IDEM_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain, times(1)).doFilter(
                org.mockito.ArgumentMatchers.any(ServletRequest.class),
                org.mockito.ArgumentMatchers.any(ServletResponse.class));
    }

    // -------------------------------------------------------------------------
    // Webhook path → filter skipped, chain called
    // -------------------------------------------------------------------------

    @Test
    void webhookPath_filterSkipped_chainCalled() throws Exception {
        MockHttpServletRequest request = postRequest("/webhooks/erp/asn", BODY);
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
        MockHttpServletRequest request = postRequest(INBOUND_PATH, BODY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            ((HttpServletResponse) res).setStatus(422);
            ((HttpServletResponse) res).getWriter()
                    .write("{\"code\":\"STATE_TRANSITION_INVALID\"}");
        };

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(422);

        // No entry should have been stored
        String storageKey = buildStorageKey(INBOUND_PATH, IDEM_KEY);
        assertThat(store.lookup(storageKey)).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Missing Idempotency-Key → filter skipped, chain called
    // -------------------------------------------------------------------------

    @Test
    void missingIdempotencyKey_filterSkipped_chainCalled() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", INBOUND_PATH);
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
    // Storage key shape: POST:{sha256(path)}:{idempotencyKey}
    // -------------------------------------------------------------------------

    @Test
    void storageKey_shape_matchesSpec() throws Exception {
        MockHttpServletRequest request = postRequest(INBOUND_PATH, BODY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            ((HttpServletResponse) res).setStatus(201);
            ((HttpServletResponse) res).getWriter().write("{\"id\":\"x\"}");
        };

        filter.doFilterInternal(request, response, chain);

        String expectedKey = buildStorageKey(INBOUND_PATH, IDEM_KEY);
        assertThat(store.lookup(expectedKey)).isPresent();
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
     * {@code POST:{sha256hex(requestURI)}:{idempotencyKey}}.
     */
    private static String buildStorageKey(String path, String idempotencyKey) {
        String pathHash = BodyHashUtil.sha256hex(path.getBytes(StandardCharsets.UTF_8));
        return "POST:" + pathHash + ":" + idempotencyKey;
    }
}
