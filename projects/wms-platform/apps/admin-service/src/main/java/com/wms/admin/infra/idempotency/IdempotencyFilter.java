package com.wms.admin.infra.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.admin.api.dto.ApiErrorEnvelope;
import com.wms.admin.application.repository.IdempotencyStore;
import com.wms.admin.application.repository.StoredResponse;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * HTTP filter enforcing {@code Idempotency-Key} semantics for mutating admin
 * endpoints. See {@code specs/services/admin-service/idempotency.md}.
 *
 * <p>Mirrors the master-service pattern. Scoped to {@code /api/v1/admin/*}.
 */
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);

    private static final String HEADER = "Idempotency-Key";
    private static final String PATH_PREFIX = "/api/v1/admin/";
    private static final Set<String> MUTATING = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final int MAX_KEY_LENGTH = 128;

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

    /** Tri-state result of the lock-acquisition phase. */
    private enum LockOutcome {
        /** Lock acquired; proceed to execute. */
        ACQUIRED,
        /** A replay or conflict response was already written to the client. */
        RESPONSE_WRITTEN,
        /** Store was unavailable or lock could not be obtained within the deadline. */
        STOP
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Optional<String> validatedKey = validateKey(request, response);
        if (validatedKey.isEmpty()) {
            return;
        }

        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);
        String method = cachedRequest.getMethod();
        String path = cachedRequest.getRequestURI();
        String requestHash = sha256Hex(canonicalizer.canonicalize(cachedRequest.getBody()));
        String storageKey = sha256Hex(validatedKey.get() + ":" + method + ":" + path);

        Optional<StoredResponse> existing;
        try {
            existing = store.lookup(storageKey);
        } catch (RuntimeException e) {
            log.error("Idempotency store unavailable on lookup", e);
            writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                    "Idempotency store is unavailable");
            return;
        }
        if (existing.isPresent() && replayOrConflict(response, existing.get(), requestHash)) {
            return;
        }

        LockOutcome lockOutcome = acquireLock(storageKey, requestHash, response);
        if (lockOutcome != LockOutcome.ACQUIRED) {
            return;
        }

        executeAndStore(cachedRequest, response, chain, storageKey, requestHash);
    }

    /**
     * Phase A — validates the {@code Idempotency-Key} header (presence + length).
     * Writes an error response and returns empty when validation fails.
     */
    private Optional<String> validateKey(HttpServletRequest request,
                                         HttpServletResponse response) throws IOException {
        String key = request.getHeader(HEADER);
        if (key == null || key.isBlank()) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "VALIDATION_ERROR",
                    "Idempotency-Key header is required on mutating endpoints");
            return Optional.empty();
        }
        if (key.length() > MAX_KEY_LENGTH) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "VALIDATION_ERROR",
                    "Idempotency-Key must be <= " + MAX_KEY_LENGTH + " chars");
            return Optional.empty();
        }
        return Optional.of(key);
    }

    /**
     * Phase B — acquires the idempotency lock for {@code storageKey}, retrying up to
     * {@link #lockWaitMax}. May write a replay or conflict response to {@code response}
     * during the retry loop if a stored result appears.
     *
     * @return {@link LockOutcome#ACQUIRED} if the lock was taken,
     *         {@link LockOutcome#RESPONSE_WRITTEN} if a replay/conflict was written,
     *         {@link LockOutcome#STOP} if the store was unavailable or the deadline expired
     */
    private LockOutcome acquireLock(String storageKey, String requestHash,
                                    HttpServletResponse response) throws IOException {
        boolean acquired;
        try {
            acquired = store.tryAcquireLock(storageKey, lockTtl);
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
                    return LockOutcome.RESPONSE_WRITTEN;
                }
                acquired = store.tryAcquireLock(storageKey, lockTtl);
            }
        } catch (RuntimeException e) {
            log.error("Idempotency store unavailable on lock", e);
            writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                    "Idempotency store is unavailable");
            return LockOutcome.STOP;
        }
        if (!acquired) {
            writeError(response, HttpServletResponse.SC_CONFLICT, "CONFLICT",
                    "Idempotent retry in progress, please retry later");
            return LockOutcome.STOP;
        }
        return LockOutcome.ACQUIRED;
    }

    /**
     * Phase C — executes the downstream filter chain and stores the response in the
     * idempotency store. Releases the lock unconditionally in the finally block.
     * Responses with status &ge; 500 or aborted by exception are not stored.
     */
    private void executeAndStore(CachedBodyHttpServletRequest cachedRequest,
                                 HttpServletResponse response,
                                 FilterChain chain,
                                 String storageKey,
                                 String requestHash) throws IOException, ServletException {
        try {
            Optional<StoredResponse> recheck = store.lookup(storageKey);
            if (recheck.isPresent() && replayOrConflict(response, recheck.get(), requestHash)) {
                return;
            }

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
                if (failure == null && status < 500) {
                    try {
                        store.put(storageKey, new StoredResponse(
                                requestHash,
                                status,
                                new String(body, StandardCharsets.UTF_8),
                                cachedResponse.getContentType(),
                                Instant.now()
                        ), ttl);
                    } catch (RuntimeException ex) {
                        log.warn("Idempotency store failed after commit (key hash={})", storageKey, ex);
                    }
                }
                cachedResponse.copyBodyToResponse();
            }
        } finally {
            try {
                store.releaseLock(storageKey);
            } catch (RuntimeException ex) {
                log.warn("Failed to release idempotency lock (key hash={})", storageKey, ex);
            }
        }
    }

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

    private void writeError(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        byte[] body = objectMapper.writeValueAsBytes(ApiErrorEnvelope.of(code, message));
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
        response.getOutputStream().flush();
    }

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
