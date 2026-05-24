package com.wms.master.adapter.in.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.master.adapter.in.web.dto.response.ApiErrorEnvelope;
import com.wms.master.application.port.out.IdempotencyStore;
import com.wms.master.application.port.out.StoredResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * HTTP filter enforcing {@code Idempotency-Key} semantics for mutating master
 * endpoints. See {@code specs/services/master-service/idempotency.md}.
 */
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);

    private static final String HEADER = "Idempotency-Key";
    private static final String PATH_PREFIX = "/api/v1/master/";
    private static final Set<String> MUTATING = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final IdempotencyStore store;
    private final RequestBodyCanonicalizer canonicalizer;
    private final ObjectMapper objectMapper;
    private final Duration ttl;
    private final Duration lockTtl;
    private final Duration lockWaitMax;
    private final Duration lockPoll;

    public IdempotencyFilter(IdempotencyStore store,
                             RequestBodyCanonicalizer canonicalizer,
                             ObjectMapper objectMapper,
                             Duration ttl) {
        this(store, canonicalizer, objectMapper, ttl,
                Duration.ofSeconds(30), Duration.ofSeconds(5), Duration.ofMillis(200));
    }

    IdempotencyFilter(IdempotencyStore store,
                      RequestBodyCanonicalizer canonicalizer,
                      ObjectMapper objectMapper,
                      Duration ttl,
                      Duration lockTtl,
                      Duration lockWaitMax,
                      Duration lockPoll) {
        this.store = store;
        this.canonicalizer = canonicalizer;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
        this.lockTtl = lockTtl;
        this.lockWaitMax = lockWaitMax;
        this.lockPoll = lockPoll;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        return !MUTATING.contains(method) || path == null || !path.startsWith(PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String key = request.getHeader(HEADER);
        if (!validateKey(key, response)) {
            return;
        }

        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);
        String requestHash = sha256Hex(canonicalizer.canonicalize(cachedRequest.getBody()));
        String storageKey = sha256Hex(key + ":" + cachedRequest.getMethod() + ":" + cachedRequest.getRequestURI());

        Optional<StoredResponse> existing = lookupSafe(storageKey, response);
        if (existing == null) {
            return; // store error already written
        }
        if (existing.isPresent() && replayOrConflict(response, existing.get(), requestHash)) {
            return;
        }

        if (!lockAndProceed(storageKey, requestHash, cachedRequest, response, chain)) {
            return;
        }
    }

    // -------------------------------------------------------------------------
    // Key validation
    // -------------------------------------------------------------------------

    /**
     * Validates the Idempotency-Key header. Returns {@code true} if valid,
     * {@code false} (and writes an error response) if invalid.
     */
    private boolean validateKey(String key, HttpServletResponse response) throws IOException {
        if (key == null || key.isBlank()) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "VALIDATION_ERROR",
                    "Idempotency-Key header is required on mutating endpoints");
            return false;
        }
        if (key.length() > 64 || !UUID_PATTERN.matcher(key).matches()) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "VALIDATION_ERROR",
                    "Idempotency-Key must be a UUID");
            return false;
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Store lookup
    // -------------------------------------------------------------------------

    /**
     * Looks up the store and returns the result. Returns {@code null} and writes
     * an error response if the store is unavailable.
     */
    private Optional<StoredResponse> lookupSafe(String storageKey, HttpServletResponse response)
            throws IOException {
        try {
            return store.lookup(storageKey);
        } catch (RuntimeException e) {
            log.error("Idempotency store unavailable on lookup", e);
            writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                    "Idempotency store is unavailable");
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Lock acquisition + request execution
    // -------------------------------------------------------------------------

    /**
     * Acquires the processing lock (with bounded wait), then executes the filter
     * chain and persists the response. Returns {@code true} if the request
     * completed normally, {@code false} if a response was already written (lock
     * timeout, store error, or replay/conflict detected after lock).
     */
    private boolean lockAndProceed(String storageKey,
                                   String requestHash,
                                   CachedBodyHttpServletRequest cachedRequest,
                                   HttpServletResponse response,
                                   FilterChain chain) throws ServletException, IOException {
        boolean acquired;
        try {
            acquired = acquireLockWithWait(storageKey, requestHash, response);
        } catch (RuntimeException e) {
            log.error("Idempotency store unavailable on lock", e);
            writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                    "Idempotency store is unavailable");
            return false;
        }
        if (!acquired) {
            // Either a replay/conflict was already written, or timeout.
            return false;
        }

        try {
            // Re-check after acquiring the lock to close the race window
            Optional<StoredResponse> recheck = store.lookup(storageKey);
            if (recheck.isPresent() && replayOrConflict(response, recheck.get(), requestHash)) {
                return false;
            }
            executeAndPersist(storageKey, requestHash, cachedRequest, response, chain);
            return true;
        } finally {
            releaseLockSafe(storageKey);
        }
    }

    /**
     * Attempts to acquire the lock within the configured wait window. During the
     * wait, intermediate lookups may shortcut via replay/conflict. Returns
     * {@code true} when the lock is held, {@code false} if timed out (and a
     * CONFLICT response has already been written) or a replay/conflict was handled.
     *
     * <p>Callers must propagate any {@link RuntimeException} from the store.
     */
    private boolean acquireLockWithWait(String storageKey,
                                        String requestHash,
                                        HttpServletResponse response) throws IOException {
        boolean acquired = store.tryAcquireLock(storageKey, lockTtl);
        long deadline = System.currentTimeMillis() + lockWaitMax.toMillis();
        while (!acquired && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(lockPoll.toMillis());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
            Optional<StoredResponse> raced = store.lookup(storageKey);
            if (raced.isPresent() && replayOrConflict(response, raced.get(), requestHash)) {
                return false; // replay/conflict written — stop polling
            }
            acquired = store.tryAcquireLock(storageKey, lockTtl);
        }
        if (!acquired) {
            writeError(response, HttpServletResponse.SC_CONFLICT, "CONFLICT",
                    "Idempotent retry in progress, please retry later");
            return false;
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Chain execution and response persistence
    // -------------------------------------------------------------------------

    /**
     * Executes the downstream filter chain, capturing the response body, and
     * persists a successful (non-5xx, no exception) response into the idempotency
     * store.
     */
    private void executeAndPersist(String storageKey,
                                   String requestHash,
                                   CachedBodyHttpServletRequest cachedRequest,
                                   HttpServletResponse response,
                                   FilterChain chain) throws ServletException, IOException {
        ContentCachingResponseWrapper cachedResponse = new ContentCachingResponseWrapper(response);
        Throwable failure = null;
        try {
            chain.doFilter(cachedRequest, cachedResponse);
        } catch (IOException | ServletException | RuntimeException ex) {
            failure = ex;
            throw ex;
        } finally {
            int status = cachedResponse.getStatus();
            byte[] body = cachedResponse.getContentAsByteArray();
            // Persist cached response on 2xx/4xx (not 5xx, not unhandled exceptions)
            if (failure == null && status < 500) {
                persistResponseSafe(storageKey, requestHash, status, body,
                        cachedResponse.getContentType());
            }
            cachedResponse.copyBodyToResponse();
        }
    }

    private void persistResponseSafe(String storageKey, String requestHash,
                                     int status, byte[] body, String contentType) {
        try {
            store.put(storageKey, new StoredResponse(
                    requestHash,
                    status,
                    new String(body, StandardCharsets.UTF_8),
                    contentType,
                    Instant.now()
            ), ttl);
        } catch (RuntimeException ex) {
            log.warn("Idempotency store failed after commit (key hash={})", storageKey, ex);
        }
    }

    private void releaseLockSafe(String storageKey) {
        try {
            store.releaseLock(storageKey);
        } catch (RuntimeException ex) {
            log.warn("Failed to release idempotency lock (key hash={})", storageKey, ex);
        }
    }

    // -------------------------------------------------------------------------
    // Replay / conflict resolution
    // -------------------------------------------------------------------------

    /**
     * Writes a replay or a DUPLICATE_REQUEST error based on the stored hash.
     * Returns {@code true} if a response was written.
     */
    private boolean replayOrConflict(HttpServletResponse response, StoredResponse cached, String requestHash)
            throws IOException {
        if (cached.requestHash().equals(requestHash)) {
            writeCached(response, cached);
            return true;
        }
        writeError(response, HttpServletResponse.SC_CONFLICT, "DUPLICATE_REQUEST",
                "Idempotency-Key reused with a different request body");
        return true;
    }

    private void writeCached(HttpServletResponse response, StoredResponse cached) throws IOException {
        response.setStatus(cached.status());
        if (cached.contentType() != null) {
            response.setContentType(cached.contentType());
        }
        if (cached.bodyJson() != null && !cached.bodyJson().isEmpty()) {
            byte[] bytes = cached.bodyJson().getBytes(StandardCharsets.UTF_8);
            response.setContentLength(bytes.length);
            response.getOutputStream().write(bytes);
            response.getOutputStream().flush();
        }
    }

    // -------------------------------------------------------------------------
    // Error writing
    // -------------------------------------------------------------------------

    private void writeError(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        byte[] body = objectMapper.writeValueAsBytes(ApiErrorEnvelope.of(code, message));
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
        response.getOutputStream().flush();
    }

    // -------------------------------------------------------------------------
    // Hashing utilities
    // -------------------------------------------------------------------------

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
