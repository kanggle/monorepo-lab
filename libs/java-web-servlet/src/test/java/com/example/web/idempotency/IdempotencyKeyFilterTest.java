package com.example.web.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Unit tests for {@link IdempotencyKeyFilter} — the unified Idempotency-Key
 * control flow. Uses an in-memory {@link IdempotencyStore}, a capturing
 * {@link IdempotencyErrorWriter}/{@link IdempotencyMetrics}, and Spring's mock
 * servlet objects (no full MVC stack).
 */
class IdempotencyKeyFilterTest {

    private static final String API_PREFIX = "/api/v1/test/";
    private static final String BODY = "{\"name\":\"widget\",\"qty\":3}";
    private static final String KEY = "key-123";

    private InMemoryStore store;
    private CapturingErrorWriter errorWriter;
    private CapturingMetrics metrics;
    private IdempotencyKeyFilter filter;

    @BeforeEach
    void setUp() {
        store = new InMemoryStore();
        errorWriter = new CapturingErrorWriter();
        metrics = new CapturingMetrics();
        IdempotencyFilterConfig config = IdempotencyFilterConfig.builder()
                .methods("POST", "PATCH")
                .applyToPrefixSkippingWebhook(API_PREFIX, "/webhooks/")
                .maxKeyLength(255)
                .lockTtl(Duration.ofSeconds(30))
                .entryTtl(Duration.ofHours(24))
                .build();
        filter = new IdempotencyKeyFilter(
                store,
                new JsonValueBodyCanonicalizer(new ObjectMapper()),
                errorWriter,
                metrics,
                config);
    }

    private MockHttpServletRequest post(String body, String key) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", API_PREFIX + "things");
        req.setRequestURI(API_PREFIX + "things");
        if (body != null) {
            req.setContent(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        if (key != null) {
            req.addHeader(IdempotencyKeyFilter.IDEMPOTENCY_KEY_HEADER, key);
        }
        return req;
    }

    /** Handler that records invocations and writes a 201 JSON response. */
    private static final class RecordingChain implements FilterChain {
        final AtomicInteger calls = new AtomicInteger();
        final String responseBody;

        RecordingChain(String responseBody) {
            this.responseBody = responseBody;
        }

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response)
                throws IOException {
            calls.incrementAndGet();
            HttpServletResponse r = (HttpServletResponse) response;
            r.setStatus(201);
            r.setContentType("application/json");
            r.getWriter().write(responseBody);
        }
    }

    @Test
    void notApplicablePath_passesThrough() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/webhooks/inbound");
        req.setRequestURI("/webhooks/inbound");
        req.addHeader(IdempotencyKeyFilter.IDEMPOTENCY_KEY_HEADER, KEY);
        MockHttpServletResponse res = new MockHttpServletResponse();
        RecordingChain chain = new RecordingChain("{}");

        filter.doFilter(req, res, chain);

        assertThat(chain.calls).hasValue(1);
        assertThat(metrics.lookups).isEmpty();
    }

    @Test
    void noKeyHeader_passesThrough() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        RecordingChain chain = new RecordingChain("{}");

        filter.doFilter(post(BODY, null), res, chain);

        assertThat(chain.calls).hasValue(1);
        assertThat(metrics.lookups).isEmpty();
    }

    @Test
    void overLengthKey_writesKeyTooLong() throws Exception {
        String longKey = "x".repeat(256);
        MockHttpServletResponse res = new MockHttpServletResponse();
        RecordingChain chain = new RecordingChain("{}");

        filter.doFilter(post(BODY, longKey), res, chain);

        assertThat(chain.calls).hasValue(0);
        assertThat(errorWriter.keyTooLong).isEqualTo(255);
        assertThat(res.getStatus()).isEqualTo(400);
    }

    @Test
    void firstRequest_proceedsCachesAndReleasesLock() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        RecordingChain chain = new RecordingChain("{\"id\":\"created\"}");

        filter.doFilter(post(BODY, KEY), res, chain);

        assertThat(chain.calls).hasValue(1);
        assertThat(res.getStatus()).isEqualTo(201);
        assertThat(res.getContentAsString()).contains("created");
        assertThat(store.responses).hasSize(1);      // response cached
        assertThat(store.locks).isEmpty();            // lock released
        assertThat(metrics.lookups).containsExactly(IdempotencyMetrics.RESULT_MISS);
    }

    @Test
    void replay_sameKeySameBody_returnsCachedResponseWithoutCallingHandler() throws Exception {
        // first request populates the cache
        filter.doFilter(post(BODY, KEY), new MockHttpServletResponse(), new RecordingChain("{\"id\":\"created\"}"));

        // retry: same key + same body
        MockHttpServletResponse res2 = new MockHttpServletResponse();
        RecordingChain chain2 = new RecordingChain("{\"id\":\"SHOULD-NOT-RUN\"}");
        filter.doFilter(post(BODY, KEY), res2, chain2);

        assertThat(chain2.calls).hasValue(0);             // handler NOT called
        assertThat(res2.getStatus()).isEqualTo(201);
        assertThat(res2.getContentAsString()).contains("created");
        assertThat(metrics.lookups).containsExactly(
                IdempotencyMetrics.RESULT_MISS, IdempotencyMetrics.RESULT_HIT);
    }

    @Test
    void conflict_sameKeyDifferentBody_writes409() throws Exception {
        filter.doFilter(post(BODY, KEY), new MockHttpServletResponse(), new RecordingChain("{\"id\":\"created\"}"));

        MockHttpServletResponse res2 = new MockHttpServletResponse();
        RecordingChain chain2 = new RecordingChain("{}");
        filter.doFilter(post("{\"name\":\"DIFFERENT\"}", KEY), res2, chain2);

        assertThat(chain2.calls).hasValue(0);
        assertThat(errorWriter.conflict).isTrue();
        assertThat(res2.getStatus()).isEqualTo(409);
        assertThat(metrics.lookups).containsExactly(
                IdempotencyMetrics.RESULT_MISS, IdempotencyMetrics.RESULT_CONFLICT);
    }

    @Test
    void lockHeld_writes503Processing() throws Exception {
        // simulate a concurrent in-flight request holding the lock
        String uriHash = BodyHashUtil.sha256hex((API_PREFIX + "things").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        store.locks.add("POST:" + uriHash + ":" + KEY);

        MockHttpServletResponse res = new MockHttpServletResponse();
        RecordingChain chain = new RecordingChain("{}");
        filter.doFilter(post(BODY, KEY), res, chain);

        assertThat(chain.calls).hasValue(0);
        assertThat(errorWriter.processing).isTrue();
        assertThat(res.getStatus()).isEqualTo(503);
        assertThat(metrics.lookups).containsExactly(IdempotencyMetrics.RESULT_CONFLICT);
    }

    @Test
    void storeLookupThrows_failsOpenAndProceeds() throws Exception {
        IdempotencyKeyFilter throwingFilter = new IdempotencyKeyFilter(
                new ThrowingStore(),
                new JsonValueBodyCanonicalizer(new ObjectMapper()),
                errorWriter,
                metrics,
                IdempotencyFilterConfig.builder()
                        .methods("POST")
                        .applyToPrefixSkippingWebhook(API_PREFIX, "/webhooks/")
                        .build());

        MockHttpServletResponse res = new MockHttpServletResponse();
        RecordingChain chain = new RecordingChain("{\"id\":\"created\"}");
        throwingFilter.doFilter(post(BODY, KEY), res, chain);

        assertThat(chain.calls).hasValue(1);              // proceeded despite store failure
        assertThat(metrics.storeFailures).isEqualTo(1);
    }

    @Test
    void nullMetrics_defaultsToNoOp() throws Exception {
        IdempotencyKeyFilter noMetricsFilter = new IdempotencyKeyFilter(
                store,
                new JsonValueBodyCanonicalizer(new ObjectMapper()),
                errorWriter,
                null,                                       // -> NO_OP
                IdempotencyFilterConfig.builder()
                        .methods("POST")
                        .applyToPrefixSkippingWebhook(API_PREFIX, "/webhooks/")
                        .build());

        MockHttpServletResponse res = new MockHttpServletResponse();
        RecordingChain chain = new RecordingChain("{}");
        noMetricsFilter.doFilter(post(BODY, KEY), res, chain);  // must not NPE

        assertThat(chain.calls).hasValue(1);
    }

    // ------------------------------------------------------------------ fixtures

    private static class InMemoryStore implements IdempotencyStore {
        final ConcurrentHashMap<String, StoredResponse> responses = new ConcurrentHashMap<>();
        final java.util.Set<String> locks = ConcurrentHashMap.newKeySet();

        @Override
        public Optional<StoredResponse> lookup(String key) {
            return Optional.ofNullable(responses.get(key));
        }

        @Override
        public boolean tryAcquireLock(String key, Duration ttl) {
            return locks.add(key);
        }

        @Override
        public void put(String key, StoredResponse response, Duration ttl) {
            responses.put(key, response);
        }

        @Override
        public void releaseLock(String key) {
            locks.remove(key);
        }
    }

    private static class ThrowingStore implements IdempotencyStore {
        @Override
        public Optional<StoredResponse> lookup(String key) {
            throw new RuntimeException("store down");
        }

        @Override
        public boolean tryAcquireLock(String key, Duration ttl) {
            return true;
        }

        @Override
        public void put(String key, StoredResponse response, Duration ttl) {
            // no-op
        }

        @Override
        public void releaseLock(String key) {
            // no-op
        }
    }

    private static class CapturingErrorWriter implements IdempotencyErrorWriter {
        boolean conflict;
        boolean processing;
        int keyTooLong = -1;

        @Override
        public void writeConflict(HttpServletResponse response) throws IOException {
            conflict = true;
            response.setStatus(409);
            response.getWriter().write("{\"code\":\"DUPLICATE_REQUEST\"}");
        }

        @Override
        public void writeProcessing(HttpServletResponse response) throws IOException {
            processing = true;
            response.setHeader("Retry-After", "1");
            response.setStatus(503);
            response.getWriter().write("{\"code\":\"PROCESSING\"}");
        }

        @Override
        public void writeKeyTooLong(HttpServletResponse response, int maxKeyLength) throws IOException {
            keyTooLong = maxKeyLength;
            response.setStatus(400);
            response.getWriter().write("{\"code\":\"VALIDATION_ERROR\"}");
        }
    }

    private static class CapturingMetrics implements IdempotencyMetrics {
        final List<String> lookups = new ArrayList<>();
        int storeFailures;

        @Override
        public void recordLookup(String result, long durationNanos) {
            lookups.add(result);
        }

        @Override
        public void recordStoreFailure() {
            storeFailures++;
        }
    }
}
