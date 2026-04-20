package com.wms.master.adapter.in.web.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wms.master.application.port.out.IdempotencyStore;
import com.wms.master.application.port.out.StoredResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class IdempotencyFilterTest {

    private static final String KEY = "11111111-2222-3333-4444-555555555555";
    private static final String PATH = "/api/v1/master/warehouses";

    /**
     * Mirrors the Spring Boot default ObjectMapper: {@code JavaTimeModule}
     * registered and timestamps written as ISO 8601 strings. Required now
     * that {@code ApiErrorEnvelope.ApiError} carries an {@link Instant}
     * timestamp (per {@code platform/error-handling.md}).
     */
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final RequestBodyCanonicalizer canonicalizer = new RequestBodyCanonicalizer(mapper);

    @Test
    void firstCall_executesChain_andStoresResponseOn2xx() throws Exception {
        RecordingStore store = new RecordingStore();
        IdempotencyFilter filter = newFilter(store);

        MockHttpServletRequest request = mutatingRequest("POST", PATH, KEY, "{\"warehouseCode\":\"WH01\"}");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            HttpServletResponse http = (HttpServletResponse) res;
            http.setStatus(201);
            http.setContentType("application/json");
            http.getWriter().write("{\"id\":\"x\"}");
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentAsString()).isEqualTo("{\"id\":\"x\"}");
        assertThat(store.putCalls.get()).isEqualTo(1);
        assertThat(store.lastPut).isNotNull();
        assertThat(store.lastPut.status()).isEqualTo(201);
        assertThat(store.lastPut.bodyJson()).isEqualTo("{\"id\":\"x\"}");
    }

    @Test
    void replay_returnsCachedResponse_doesNotExecuteChain() throws Exception {
        RecordingStore store = new RecordingStore();
        String body = "{\"warehouseCode\":\"WH01\"}";
        String hash = store.hash(canonicalizer.canonicalize(body.getBytes()));
        store.seed(KEY, "POST", PATH, new StoredResponse(
                hash, 201, "{\"id\":\"cached\"}", "application/json", Instant.now()));

        IdempotencyFilter filter = newFilter(store);
        MockHttpServletRequest request = mutatingRequest("POST", PATH, KEY, body);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicInteger chainCalls = new AtomicInteger();
        FilterChain chain = (req, res) -> chainCalls.incrementAndGet();

        filter.doFilter(request, response, chain);

        assertThat(chainCalls.get()).isZero();
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentAsString()).isEqualTo("{\"id\":\"cached\"}");
    }

    @Test
    void conflict_sameKey_differentBody_returns409_DUPLICATE_REQUEST() throws Exception {
        RecordingStore store = new RecordingStore();
        String otherHash = store.hash("different-hash-input");
        store.seed(KEY, "POST", PATH, new StoredResponse(
                otherHash, 201, "{\"id\":\"first\"}", "application/json", Instant.now()));

        IdempotencyFilter filter = newFilter(store);
        MockHttpServletRequest request = mutatingRequest("POST", PATH, KEY, "{\"warehouseCode\":\"WH01\"}");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, passThrough());

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).contains("DUPLICATE_REQUEST");
    }

    @Test
    void missingKey_returns400_VALIDATION_ERROR() throws Exception {
        IdempotencyFilter filter = newFilter(new RecordingStore());
        MockHttpServletRequest request = mutatingRequest("POST", PATH, null, "{}");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, passThrough());

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString())
                .contains("VALIDATION_ERROR")
                .contains("Idempotency-Key header is required");
    }

    @Test
    void malformedKey_returns400_VALIDATION_ERROR() throws Exception {
        IdempotencyFilter filter = newFilter(new RecordingStore());
        MockHttpServletRequest request = mutatingRequest("POST", PATH, "not-a-uuid", "{}");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, passThrough());

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString())
                .contains("VALIDATION_ERROR")
                .contains("must be a UUID");
    }

    @Test
    void redisOutage_returns503_SERVICE_UNAVAILABLE() throws Exception {
        IdempotencyStore failingStore = new IdempotencyStore() {
            @Override public Optional<StoredResponse> lookup(String storageKey) {
                throw new RuntimeException("Redis is down");
            }
            @Override public void put(String s, StoredResponse r, Duration t) {}
            @Override public boolean tryAcquireLock(String s, Duration t) { return false; }
            @Override public void releaseLock(String s) {}
        };
        IdempotencyFilter filter = newFilter(failingStore);
        MockHttpServletRequest request = mutatingRequest("POST", PATH, KEY, "{}");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, passThrough());

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("SERVICE_UNAVAILABLE");
    }

    @Test
    void getRequest_bypassesFilter() throws Exception {
        RecordingStore store = new RecordingStore();
        IdempotencyFilter filter = newFilter(store);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", PATH);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicInteger chainCalls = new AtomicInteger();

        filter.doFilter(request, response, (req, res) -> {
            chainCalls.incrementAndGet();
            ((HttpServletResponse) res).setStatus(200);
        });

        assertThat(chainCalls.get()).isEqualTo(1);
        assertThat(store.putCalls.get()).isZero();
    }

    @Test
    void nonMasterPath_bypassesFilter() throws Exception {
        RecordingStore store = new RecordingStore();
        IdempotencyFilter filter = newFilter(store);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            ((HttpServletResponse) res).setStatus(200);
        });

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(store.lookupCalls.get()).isZero();
    }

    @Test
    void serverError5xx_isNotCached() throws Exception {
        RecordingStore store = new RecordingStore();
        IdempotencyFilter filter = newFilter(store);
        MockHttpServletRequest request = mutatingRequest("POST", PATH, KEY, "{}");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            HttpServletResponse http = (HttpServletResponse) res;
            http.setStatus(500);
            http.getWriter().write("oops");
        });

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(store.putCalls.get()).isZero();
    }

    @Test
    void clientError4xx_isCached_soRetryGetsSameResponse() throws Exception {
        RecordingStore store = new RecordingStore();
        IdempotencyFilter filter = newFilter(store);
        MockHttpServletRequest request = mutatingRequest("POST", PATH, KEY, "{}");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            HttpServletResponse http = (HttpServletResponse) res;
            http.setStatus(400);
            http.getWriter().write("{\"error\":{\"code\":\"VALIDATION_ERROR\"}}");
        });

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(store.putCalls.get()).isEqualTo(1);
        assertThat(store.lastPut.status()).isEqualTo(400);
    }

    // ---------- helpers ----------

    private IdempotencyFilter newFilter(IdempotencyStore store) {
        return new IdempotencyFilter(
                store, canonicalizer, mapper,
                Duration.ofSeconds(60),
                Duration.ofSeconds(5),
                Duration.ofMillis(200),
                Duration.ofMillis(50));
    }

    private static MockHttpServletRequest mutatingRequest(String method, String path, String key, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);
        request.setContentType("application/json");
        if (key != null) {
            request.addHeader("Idempotency-Key", key);
        }
        if (body != null) {
            request.setContent(body.getBytes());
        }
        return request;
    }

    private static FilterChain passThrough() {
        return (req, res) -> {
            // no-op
        };
    }

    /**
     * Fake store that records interactions and lets tests seed entries with a
     * fully-computed storage key matching the filter's SHA-256 formula.
     */
    private static final class RecordingStore implements IdempotencyStore {

        private final java.util.Map<String, StoredResponse> entries = new java.util.concurrent.ConcurrentHashMap<>();
        private final java.util.Map<String, Boolean> locks = new java.util.concurrent.ConcurrentHashMap<>();
        final AtomicInteger lookupCalls = new AtomicInteger();
        final AtomicInteger putCalls = new AtomicInteger();
        StoredResponse lastPut;

        void seed(String key, String method, String path, StoredResponse response) {
            entries.put(storageKey(key, method, path), response);
        }

        String hash(String input) {
            return sha256Hex(input);
        }

        private static String storageKey(String key, String method, String path) {
            return sha256Hex(key + ":" + method + ":" + path);
        }

        private static String sha256Hex(String input) {
            try {
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
                return java.util.HexFormat.of().formatHex(md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override public Optional<StoredResponse> lookup(String storageKey) {
            lookupCalls.incrementAndGet();
            return Optional.ofNullable(entries.get(storageKey));
        }
        @Override public void put(String storageKey, StoredResponse response, Duration ttl) {
            putCalls.incrementAndGet();
            lastPut = response;
            entries.put(storageKey, response);
        }
        @Override public boolean tryAcquireLock(String storageKey, Duration ttl) {
            return locks.putIfAbsent(storageKey, Boolean.TRUE) == null;
        }
        @Override public void releaseLock(String storageKey) {
            locks.remove(storageKey);
        }
    }
}
