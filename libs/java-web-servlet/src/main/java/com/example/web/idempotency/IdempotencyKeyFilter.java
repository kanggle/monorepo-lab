package com.example.web.idempotency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Shared REST Idempotency-Key filter (ADR-MONO-038 I1) — the unified control
 * flow previously copied per WMS service, parameterised by an
 * {@link IdempotencyFilterConfig}, an {@link IdempotencyStore}, a
 * {@link BodyCanonicalizer}, an {@link IdempotencyErrorWriter}, and optional
 * {@link IdempotencyMetrics}.
 *
 * <h2>Flow</h2>
 * <ol>
 *   <li>Skip requests the config does not apply to (method / path).</li>
 *   <li>Skip requests without an {@code Idempotency-Key} header (controller
 *       validation handles the 400).</li>
 *   <li>If a positive {@code maxKeyLength} is configured, reject over-length
 *       keys (400 via the error writer).</li>
 *   <li>Cache the request body so it is replayable for the message converters.</li>
 *   <li>Compute the canonical body hash.</li>
 *   <li>Look up {@code {METHOD}:sha256(uri):{key}}:
 *     <ul>
 *       <li>hit + same hash → replay the cached response;</li>
 *       <li>hit + different hash → 409 {@code DUPLICATE_REQUEST};</li>
 *       <li>miss → acquire a lock; 503 {@code PROCESSING} if held.</li>
 *     </ul>
 *   </li>
 *   <li>Proceed, cache a 2xx response, always release the lock, flush.</li>
 * </ol>
 *
 * <h2>Fail-open</h2>
 * <p>Any {@link IdempotencyStore} exception (lookup / lock / put / release) is
 * logged + a {@link IdempotencyMetrics#recordStoreFailure()} emitted, and the
 * request proceeds without the idempotency guarantee — the WMS
 * availability-over-correctness posture, backstopped by domain-layer unique
 * constraints.
 *
 * <p>Register at order {@code HIGHEST_PRECEDENCE + 20} (after Spring Security,
 * before DispatcherServlet), as the per-service filter configs did.
 */
public class IdempotencyKeyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyKeyFilter.class);

    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final IdempotencyStore store;
    private final BodyCanonicalizer canonicalizer;
    private final IdempotencyErrorWriter errorWriter;
    private final IdempotencyMetrics metrics;
    private final IdempotencyFilterConfig config;

    public IdempotencyKeyFilter(IdempotencyStore store,
                                BodyCanonicalizer canonicalizer,
                                IdempotencyErrorWriter errorWriter,
                                IdempotencyMetrics metrics,
                                IdempotencyFilterConfig config) {
        this.store = store;
        this.canonicalizer = canonicalizer;
        this.errorWriter = errorWriter;
        this.metrics = metrics != null ? metrics : IdempotencyMetrics.NO_OP;
        this.config = config;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // Step 1: skip if the config does not apply (method / path / webhook).
        if (!config.shouldApply(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Step 2: skip if no Idempotency-Key header (controller returns 400).
        String idempotencyKey = request.getHeader(IDEMPOTENCY_KEY_HEADER);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Step 3: optional key-length guard.
        if (config.maxKeyLength() > 0 && idempotencyKey.length() > config.maxKeyLength()) {
            errorWriter.writeKeyTooLong(response, config.maxKeyLength());
            return;
        }

        // Step 4: cache the request body so DispatcherServlet can re-read it.
        CachedBodyHttpServletRequestWrapper cachedRequest;
        try {
            cachedRequest = new CachedBodyHttpServletRequestWrapper(request);
        } catch (IOException e) {
            log.warn("Idempotency filter: failed to cache request body — skipping idempotency", e);
            filterChain.doFilter(request, response);
            return;
        }

        // Step 5: canonical body hash.
        String requestBodyHash = canonicalizer.hash(cachedRequest.getCachedBody());

        // Step 6: storage key {METHOD}:{sha256(uri)}:{key}.
        String requestUriHash = BodyHashUtil.sha256hex(
                request.getRequestURI().getBytes(StandardCharsets.UTF_8));
        String storageKey = request.getMethod().toUpperCase(java.util.Locale.ROOT)
                + ":" + requestUriHash + ":" + idempotencyKey;

        // Step 7: lookup (timed).
        long start = System.nanoTime();
        Optional<StoredResponse> stored;
        try {
            stored = store.lookup(storageKey);
        } catch (Exception e) {
            log.warn("Idempotency filter: store lookup failed — proceeding without idempotency check", e);
            metrics.recordStoreFailure();
            metrics.recordLookup(IdempotencyMetrics.RESULT_MISS, System.nanoTime() - start);
            filterChain.doFilter(cachedRequest, response);
            return;
        }

        if (stored.isPresent()) {
            StoredResponse entry = stored.get();
            if (requestBodyHash.equals(entry.requestHash())) {
                metrics.recordLookup(IdempotencyMetrics.RESULT_HIT, System.nanoTime() - start);
                replayResponse(response, entry);
                return;
            }
            metrics.recordLookup(IdempotencyMetrics.RESULT_CONFLICT, System.nanoTime() - start);
            errorWriter.writeConflict(response);
            return;
        }

        // Step 8: not present — try to acquire the lock.
        boolean lockAcquired;
        try {
            lockAcquired = store.tryAcquireLock(storageKey, config.lockTtl());
        } catch (Exception e) {
            log.warn("Idempotency filter: lock acquisition failed — proceeding without idempotency", e);
            metrics.recordStoreFailure();
            metrics.recordLookup(IdempotencyMetrics.RESULT_MISS, System.nanoTime() - start);
            filterChain.doFilter(cachedRequest, response);
            return;
        }

        if (!lockAcquired) {
            metrics.recordLookup(IdempotencyMetrics.RESULT_CONFLICT, System.nanoTime() - start);
            errorWriter.writeProcessing(response);
            return;
        }

        metrics.recordLookup(IdempotencyMetrics.RESULT_MISS, System.nanoTime() - start);

        // Step 9 + 10: proceed, cache a 2xx, always release the lock, flush.
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(cachedRequest, responseWrapper);
        } finally {
            int status = responseWrapper.getStatus();
            if (status >= 200 && status < 300) {
                byte[] responseBodyBytes = responseWrapper.getContentAsByteArray();
                String responseBody = new String(responseBodyBytes, StandardCharsets.UTF_8);
                String contentType = responseWrapper.getContentType() != null
                        ? responseWrapper.getContentType()
                        : MediaType.APPLICATION_JSON_VALUE;
                try {
                    StoredResponse toStore = new StoredResponse(
                            requestBodyHash, status, responseBody, contentType, java.time.Instant.now());
                    store.put(storageKey, toStore, config.entryTtl());
                } catch (Exception e) {
                    log.warn("Idempotency filter: failed to store response — idempotency cache miss on retry", e);
                    metrics.recordStoreFailure();
                }
            }
            try {
                store.releaseLock(storageKey);
            } catch (Exception e) {
                log.warn("Idempotency filter: failed to release lock for key={}", storageKey, e);
                metrics.recordStoreFailure();
            }
            responseWrapper.copyBodyToResponse();
        }
    }

    private void replayResponse(HttpServletResponse response, StoredResponse entry) throws IOException {
        response.setStatus(entry.status());
        response.setContentType(entry.contentType() != null
                ? entry.contentType()
                : MediaType.APPLICATION_JSON_VALUE);
        byte[] body = entry.bodyJson() != null
                ? entry.bodyJson().getBytes(StandardCharsets.UTF_8)
                : new byte[0];
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
        response.getOutputStream().flush();
    }
}
