package com.wms.admin.infra.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.web.idempotency.BodyCanonicalizer;
import com.example.web.idempotency.IdempotencyStore;
import com.example.web.idempotency.JsonTreeBodyCanonicalizer;
import com.example.web.idempotency.StoredResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Unit coverage for {@link IdempotencyFilter} — the {@code Idempotency-Key}
 * enforcement filter that had ZERO test coverage. Constructs the filter directly
 * with an in-memory / stub {@link IdempotencyStore} and Spring's mock servlet
 * request/response + a recording {@link FilterChain}; no Spring context or
 * Testcontainer needed. Covers TASK-BE-525 AC-2.
 */
class IdempotencyFilterTest {

    private static final String ADMIN_PATH = "/api/v1/admin/users";
    private static final Duration TTL = Duration.ofMinutes(10);

    private ObjectMapper mapper;
    private BodyCanonicalizer canonicalizer;

    @BeforeEach
    void setUp() {
        // Mirror the production ObjectMapper (Spring Boot auto-registers JavaTimeModule):
        // the error envelope the filter emits carries an Instant `timestamp`, so the
        // mapper must support jsr310 or 400/409/503 serialization throws.
        mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        canonicalizer = new JsonTreeBodyCanonicalizer(mapper);
    }

    private IdempotencyFilter filter(IdempotencyStore store) {
        return new IdempotencyFilter(store, canonicalizer, mapper, TTL);
    }

    // ----- header validation -------------------------------------------------

    @Test
    @DisplayName("missing Idempotency-Key on a mutating admin request -> 400 VALIDATION_ERROR")
    void missingHeader_returns400() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingChain chain = new RecordingChain(201, "{}", "application/json");

        filter(new InMemoryIdempotencyStore())
                .doFilter(request("POST", ADMIN_PATH, null, "{}"), response, chain);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(errorCode(response)).isEqualTo("VALIDATION_ERROR");
        assertThat(chain.invocations).isZero();
    }

    @Test
    @DisplayName("blank Idempotency-Key -> 400 VALIDATION_ERROR")
    void blankHeader_returns400() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingChain chain = new RecordingChain(201, "{}", "application/json");

        filter(new InMemoryIdempotencyStore())
                .doFilter(request("POST", ADMIN_PATH, "   ", "{}"), response, chain);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(errorCode(response)).isEqualTo("VALIDATION_ERROR");
        assertThat(chain.invocations).isZero();
    }

    @Test
    @DisplayName("Idempotency-Key longer than 128 chars -> 400 VALIDATION_ERROR")
    void keyTooLong_returns400() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingChain chain = new RecordingChain(201, "{}", "application/json");
        String longKey = "k".repeat(129);

        filter(new InMemoryIdempotencyStore())
                .doFilter(request("POST", ADMIN_PATH, longKey, "{}"), response, chain);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(errorCode(response)).isEqualTo("VALIDATION_ERROR");
        assertThat(chain.invocations).isZero();
    }

    // ----- passthrough (shouldNotFilter) -------------------------------------

    @Test
    @DisplayName("GET (non-mutating) admin request -> passthrough, chain invoked, no key required")
    void getRequest_passesThrough() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingChain chain = new RecordingChain(200, null, null);

        // No Idempotency-Key header at all; a mutating request would 400.
        filter(new InMemoryIdempotencyStore())
                .doFilter(request("GET", ADMIN_PATH, null, null), response, chain);

        assertThat(chain.invocations).isEqualTo(1);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("POST outside /api/v1/admin/ -> passthrough, chain invoked, no key required")
    void nonAdminPath_passesThrough() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingChain chain = new RecordingChain(200, null, null);

        filter(new InMemoryIdempotencyStore())
                .doFilter(request("POST", "/api/v1/public/ping", null, "{}"), response, chain);

        assertThat(chain.invocations).isEqualTo(1);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    // ----- first-call / replay / mismatch ------------------------------------

    @Test
    @DisplayName("first call executes the chain and returns its response")
    void firstCall_executesChain() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingChain chain = new RecordingChain(201, "{\"id\":\"u1\"}", "application/json");

        filter(new InMemoryIdempotencyStore())
                .doFilter(request("POST", ADMIN_PATH, "key-1", "{\"a\":1}"), response, chain);

        assertThat(chain.invocations).isEqualTo(1);
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentAsString()).contains("u1");
    }

    @Test
    @DisplayName("replay: same key + same body -> cached status/body/content-type WITHOUT re-invoking the chain")
    void replay_sameKeySameBody_returnsCachedWithoutChain() throws Exception {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();

        MockHttpServletResponse first = new MockHttpServletResponse();
        RecordingChain firstChain = new RecordingChain(201, "{\"id\":\"u1\"}", "application/json");
        filter(store).doFilter(request("POST", ADMIN_PATH, "key-1", "{\"a\":1}"), first, firstChain);
        assertThat(firstChain.invocations).isEqualTo(1);

        MockHttpServletResponse replay = new MockHttpServletResponse();
        RecordingChain replayChain = new RecordingChain(500, "SHOULD-NOT-RUN", "text/plain");
        filter(store).doFilter(request("POST", ADMIN_PATH, "key-1", "{\"a\":1}"), replay, replayChain);

        assertThat(replayChain.invocations).isZero();
        assertThat(replay.getStatus()).isEqualTo(201);
        assertThat(replay.getContentAsString()).contains("u1");
        assertThat(replay.getContentType()).contains("application/json");
    }

    @Test
    @DisplayName("same key + different canonical body -> 409 DUPLICATE_REQUEST")
    void sameKeyDifferentBody_returns409() throws Exception {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();

        MockHttpServletResponse first = new MockHttpServletResponse();
        RecordingChain firstChain = new RecordingChain(201, "{\"id\":\"u1\"}", "application/json");
        filter(store).doFilter(request("POST", ADMIN_PATH, "key-1", "{\"a\":1}"), first, firstChain);

        MockHttpServletResponse conflict = new MockHttpServletResponse();
        RecordingChain conflictChain = new RecordingChain(201, "{}", "application/json");
        filter(store).doFilter(request("POST", ADMIN_PATH, "key-1", "{\"a\":2}"), conflict, conflictChain);

        assertThat(conflict.getStatus()).isEqualTo(409);
        assertThat(errorCode(conflict)).isEqualTo("DUPLICATE_REQUEST");
        assertThat(conflictChain.invocations).isZero();
    }

    // ----- store outage / lock / 5xx-not-cached ------------------------------

    @Test
    @DisplayName("store outage on lookup -> 503 SERVICE_UNAVAILABLE, chain not invoked")
    void storeOutage_returns503() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingChain chain = new RecordingChain(201, "{}", "application/json");

        filter(new ThrowingStore())
                .doFilter(request("POST", ADMIN_PATH, "key-1", "{\"a\":1}"), response, chain);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(errorCode(response)).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(chain.invocations).isZero();
    }

    @Test
    @DisplayName("lock cannot be acquired within the deadline -> 409 CONFLICT, chain not invoked")
    void lockTimeout_returns409Conflict() throws Exception {
        // Package-private ctor with lockWaitMax=0 makes the retry loop non-blocking:
        // a store that always refuses the lock (and holds no cached result) forces the
        // filter down the lock-timeout branch immediately, so this stays a fast unit test.
        IdempotencyFilter lockFilter = new IdempotencyFilter(new LockRefusingStore(), canonicalizer,
                mapper, TTL, Duration.ofSeconds(30), Duration.ZERO, Duration.ofMillis(1));
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingChain chain = new RecordingChain(201, "{}", "application/json");

        lockFilter.doFilter(request("POST", ADMIN_PATH, "key-1", "{\"a\":1}"), response, chain);

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(errorCode(response)).isEqualTo("CONFLICT");
        assertThat(chain.invocations).isZero();
    }

    @Test
    @DisplayName("a >= 500 response is NOT cached (a second identical call re-invokes the chain)")
    void serverError_notCached() throws Exception {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();

        MockHttpServletResponse first = new MockHttpServletResponse();
        RecordingChain firstChain = new RecordingChain(500, "{\"error\":\"boom\"}", "application/json");
        filter(store).doFilter(request("POST", ADMIN_PATH, "key-1", "{\"a\":1}"), first, firstChain);
        assertThat(first.getStatus()).isEqualTo(500);
        assertThat(firstChain.invocations).isEqualTo(1);

        // Identical retry: because the 500 was not cached, the chain must run again.
        MockHttpServletResponse second = new MockHttpServletResponse();
        RecordingChain secondChain = new RecordingChain(201, "{\"id\":\"u1\"}", "application/json");
        filter(store).doFilter(request("POST", ADMIN_PATH, "key-1", "{\"a\":1}"), second, secondChain);

        assertThat(secondChain.invocations).isEqualTo(1);
        assertThat(second.getStatus()).isEqualTo(201);
    }

    // ----- helpers -----------------------------------------------------------

    private MockHttpServletRequest request(String method, String uri, String key, String body)
            throws IOException {
        MockHttpServletRequest req = new MockHttpServletRequest(method, uri);
        req.setRequestURI(uri);
        if (key != null) {
            req.addHeader("Idempotency-Key", key);
        }
        if (body != null) {
            req.setContent(body.getBytes(StandardCharsets.UTF_8));
            req.setContentType("application/json");
        }
        return req;
    }

    private String errorCode(MockHttpServletResponse response) throws Exception {
        JsonNode tree = mapper.readTree(response.getContentAsString());
        return tree.path("error").path("code").asText();
    }

    /** Records how many times the downstream chain was invoked and writes a fixed response. */
    private static final class RecordingChain implements FilterChain {
        private int invocations = 0;
        private final int status;
        private final String body;
        private final String contentType;

        RecordingChain(int status, String body, String contentType) {
            this.status = status;
            this.body = body;
            this.contentType = contentType;
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) throws IOException {
            invocations++;
            HttpServletResponse resp = (HttpServletResponse) response;
            resp.setStatus(status);
            if (contentType != null) {
                resp.setContentType(contentType);
            }
            if (body != null) {
                resp.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    /** Store whose lookup always throws — simulates a backing-store outage. */
    private static final class ThrowingStore implements IdempotencyStore {
        @Override
        public Optional<StoredResponse> lookup(String key) {
            throw new IllegalStateException("store down");
        }

        @Override
        public boolean tryAcquireLock(String key, Duration ttl) {
            throw new IllegalStateException("store down");
        }

        @Override
        public void put(String key, StoredResponse response, Duration ttl) {
            throw new IllegalStateException("store down");
        }

        @Override
        public void releaseLock(String key) {
            // no-op
        }
    }

    /** Store that never returns a cached response and always refuses the lock. */
    private static final class LockRefusingStore implements IdempotencyStore {
        @Override
        public Optional<StoredResponse> lookup(String key) {
            return Optional.empty();
        }

        @Override
        public boolean tryAcquireLock(String key, Duration ttl) {
            return false;
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
}
