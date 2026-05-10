package com.wms.outbound.adapter.in.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.outbound.adapter.in.web.dto.response.ApiErrorEnvelope;
import com.wms.outbound.application.port.out.IdempotencyStore;
import com.wms.outbound.application.port.out.StoredResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Implements the REST Idempotency-Key lifecycle for {@code outbound-service}
 * defined in {@code specs/services/outbound-service/idempotency.md} §1.
 *
 * <h2>Flow</h2>
 * <ol>
 *   <li>Skip non-mutating methods and webhook paths.</li>
 *   <li>Skip requests without an {@code Idempotency-Key} header (controller
 *       {@code requireIdempotencyKey} guard handles the 400).</li>
 *   <li>Validate header length (≤ 255 chars per T1) — over → 400.</li>
 *   <li>Read and cache the request body so it is replayable for Spring's
 *       message converters after this filter has consumed the input stream.</li>
 *   <li>Compute a canonical body hash (sorted-keys JSON → SHA-256).</li>
 *   <li>Look up the storage key in {@link IdempotencyStore}:
 *       <ul>
 *         <li>Hit + same hash → replay cached response.</li>
 *         <li>Hit + different hash → 409 {@code DUPLICATE_REQUEST}.</li>
 *         <li>Miss → try to acquire a lock; 503 if lock held.</li>
 *       </ul>
 *   </li>
 *   <li>Proceed with the chain, cache a 2xx response, always release the lock.</li>
 * </ol>
 *
 * <p>The filter is registered via {@code IdempotencyFilterConfig} at order
 * {@code HIGHEST_PRECEDENCE + 20} — after Spring Security, before
 * DispatcherServlet.
 *
 * <h2>Fail-open policy</h2>
 *
 * <p>Per task spec § Failure Scenarios + the WMS availability-over-correctness
 * trade-off: if Redis is unavailable for the lookup or lock-acquire path, we
 * log + emit a metric and let the request proceed. The domain-layer backstops
 * (unique constraints + status guards) listed in {@code idempotency.md} §1.7
 * keep the system from doubling effects in practice — they remain effective
 * after Redis TTL expiry too. This is the same posture the inbound-service
 * filter takes.
 *
 * <h2>Metrics</h2>
 * <ul>
 *   <li>{@code outbound.idempotency.lookup.count{result=hit|miss|conflict}}
 *       — counter, one increment per request that reached the lookup step.</li>
 *   <li>{@code outbound.idempotency.lookup.duration} — timer covering the
 *       store lookup + lock attempt phase.</li>
 *   <li>{@code outbound.idempotency.store.failure} — counter incremented when
 *       lookup, lock, put, or release throws.</li>
 * </ul>
 */
public class OutboundIdempotencyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(OutboundIdempotencyFilter.class);

    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    static final String OUTBOUND_API_PREFIX = "/api/v1/outbound/";
    static final String WEBHOOK_PREFIX = "/webhooks/";
    static final int MAX_KEY_LENGTH = 255;
    static final Duration LOCK_TTL = Duration.ofSeconds(30);
    static final Duration ENTRY_TTL = Duration.ofHours(24);

    static final String METRIC_LOOKUP_COUNT = "outbound.idempotency.lookup.count";
    static final String METRIC_LOOKUP_DURATION = "outbound.idempotency.lookup.duration";
    static final String METRIC_STORE_FAILURE = "outbound.idempotency.store.failure";
    static final String TAG_RESULT = "result";
    static final String RESULT_HIT = "hit";
    static final String RESULT_MISS = "miss";
    static final String RESULT_CONFLICT = "conflict";

    private final IdempotencyStore idempotencyStore;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public OutboundIdempotencyFilter(IdempotencyStore idempotencyStore,
                                     ObjectMapper objectMapper,
                                     MeterRegistry meterRegistry) {
        this.idempotencyStore = idempotencyStore;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // Step 1: skip if not a mutating call to /api/v1/outbound/ OR path is /webhooks/**
        if (!shouldApply(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Step 2: skip if no Idempotency-Key header (controller will return 400)
        String idempotencyKey = request.getHeader(IDEMPOTENCY_KEY_HEADER);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Step 3: validate header length (T1: opaque string ≤ 255 chars)
        if (idempotencyKey.length() > MAX_KEY_LENGTH) {
            writeKeyTooLongError(response);
            return;
        }

        // Step 4: cache the request body so it can be re-read by DispatcherServlet
        CachedBodyHttpServletRequestWrapper cachedRequest;
        try {
            cachedRequest = new CachedBodyHttpServletRequestWrapper(request);
        } catch (IOException e) {
            log.warn("Idempotency filter: failed to cache request body — skipping idempotency", e);
            filterChain.doFilter(request, response);
            return;
        }

        // Step 5: compute request body hash
        byte[] bodyBytes = cachedRequest.getCachedBody();
        String requestBodyHash = BodyHashUtil.computeHash(bodyBytes, objectMapper);

        // Step 6: build storage key: {METHOD}:{sha256(requestURI)}:{idempotencyKey}
        // The {method, path_hash} prefix makes the same key reused across
        // endpoints independent (idempotency.md §1.3). Different HTTP methods
        // on the same path are also independent.
        String method = request.getMethod().toUpperCase();
        String requestUriHash = BodyHashUtil.sha256hex(
                request.getRequestURI().getBytes(StandardCharsets.UTF_8));
        String storageKey = method + ":" + requestUriHash + ":" + idempotencyKey;

        // Step 7: lookup (timed)
        Timer.Sample sample = meterRegistry != null ? Timer.start(meterRegistry) : null;
        Optional<StoredResponse> stored;
        try {
            stored = idempotencyStore.lookup(storageKey);
        } catch (Exception e) {
            log.warn("Idempotency filter: store lookup failed — proceeding without idempotency check", e);
            recordStoreFailure();
            stopTimer(sample, RESULT_MISS);
            filterChain.doFilter(cachedRequest, response);
            return;
        }

        if (stored.isPresent()) {
            StoredResponse entry = stored.get();
            if (requestBodyHash.equals(entry.requestHash())) {
                // Same key + same body → replay
                recordLookupResult(RESULT_HIT);
                stopTimer(sample, RESULT_HIT);
                replayResponse(response, entry);
                return;
            } else {
                // Same key + different body → 409 DUPLICATE_REQUEST
                recordLookupResult(RESULT_CONFLICT);
                stopTimer(sample, RESULT_CONFLICT);
                writeDuplicateRequestError(response);
                return;
            }
        }

        // Step 8: not present — try to acquire lock
        boolean lockAcquired;
        try {
            lockAcquired = idempotencyStore.tryAcquireLock(storageKey, LOCK_TTL);
        } catch (Exception e) {
            log.warn("Idempotency filter: lock acquisition failed — proceeding without idempotency", e);
            recordStoreFailure();
            stopTimer(sample, RESULT_MISS);
            filterChain.doFilter(cachedRequest, response);
            return;
        }

        if (!lockAcquired) {
            // Lock held by a concurrent request → 503
            recordLookupResult(RESULT_CONFLICT);
            stopTimer(sample, RESULT_CONFLICT);
            writeProcessingError(response);
            return;
        }

        recordLookupResult(RESULT_MISS);
        stopTimer(sample, RESULT_MISS);

        // Step 9 + 10: proceed with chain, cache 2xx, always release lock
        ContentCachingResponseWrapper responseWrapper =
                new ContentCachingResponseWrapper(response);
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
                            requestBodyHash,
                            status,
                            responseBody,
                            contentType,
                            Instant.now());
                    idempotencyStore.put(storageKey, toStore, ENTRY_TTL);
                } catch (Exception e) {
                    log.warn("Idempotency filter: failed to store response — idempotency cache miss on retry", e);
                    recordStoreFailure();
                }
            }
            try {
                idempotencyStore.releaseLock(storageKey);
            } catch (Exception e) {
                log.warn("Idempotency filter: failed to release lock for key={}", storageKey, e);
                recordStoreFailure();
            }
            // Step 11: flush cached bytes to real response
            responseWrapper.copyBodyToResponse();
        }
    }

    private boolean shouldApply(HttpServletRequest request) {
        String method = request.getMethod();
        if (!HttpMethod.POST.name().equalsIgnoreCase(method)
                && !HttpMethod.PATCH.name().equalsIgnoreCase(method)
                && !HttpMethod.PUT.name().equalsIgnoreCase(method)
                && !HttpMethod.DELETE.name().equalsIgnoreCase(method)) {
            return false;
        }
        String path = request.getRequestURI();
        if (path.startsWith(WEBHOOK_PREFIX)) {
            return false;
        }
        return path.startsWith(OUTBOUND_API_PREFIX);
    }

    private void replayResponse(HttpServletResponse response, StoredResponse entry)
            throws IOException {
        response.setStatus(entry.status());
        if (entry.contentType() != null) {
            response.setContentType(entry.contentType());
        } else {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        }
        byte[] body = entry.bodyJson() != null
                ? entry.bodyJson().getBytes(StandardCharsets.UTF_8)
                : new byte[0];
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
        response.getOutputStream().flush();
    }

    private void writeDuplicateRequestError(HttpServletResponse response) throws IOException {
        ApiErrorEnvelope envelope = ApiErrorEnvelope.of(
                "DUPLICATE_REQUEST",
                "Idempotency-Key already used with a different request body");
        writeJsonError(response, HttpServletResponse.SC_CONFLICT, envelope);
    }

    private void writeKeyTooLongError(HttpServletResponse response) throws IOException {
        ApiErrorEnvelope envelope = ApiErrorEnvelope.of(
                "VALIDATION_ERROR",
                "Idempotency-Key header exceeds " + MAX_KEY_LENGTH + " characters");
        writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, envelope);
    }

    private void writeProcessingError(HttpServletResponse response) throws IOException {
        response.setHeader("Retry-After", "1");
        ApiErrorEnvelope envelope = ApiErrorEnvelope.of(
                "PROCESSING",
                "Request is being processed");
        writeJsonError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, envelope);
    }

    private void writeJsonError(HttpServletResponse response, int status,
                                ApiErrorEnvelope envelope) throws IOException {
        byte[] body = objectMapper.writeValueAsBytes(envelope);
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
        response.getOutputStream().flush();
    }

    private void recordLookupResult(String result) {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter(METRIC_LOOKUP_COUNT, TAG_RESULT, result).increment();
    }

    private void recordStoreFailure() {
        if (meterRegistry == null) {
            return;
        }
        meterRegistry.counter(METRIC_STORE_FAILURE).increment();
    }

    private void stopTimer(Timer.Sample sample, String result) {
        if (sample == null || meterRegistry == null) {
            return;
        }
        sample.stop(Timer.builder(METRIC_LOOKUP_DURATION)
                .tag(TAG_RESULT, result)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry));
    }
}
