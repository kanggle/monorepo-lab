package com.wms.inbound.adapter.in.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.inbound.adapter.in.web.dto.response.ApiErrorEnvelope;
import com.wms.inbound.application.port.out.IdempotencyStore;
import com.wms.inbound.application.port.out.StoredResponse;
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
 * Implements the REST Idempotency-Key lifecycle defined in
 * {@code specs/services/inbound-service/idempotency.md} §1.
 *
 * <h2>Flow</h2>
 * <ol>
 *   <li>Skip non-POST requests and webhook paths.</li>
 *   <li>Skip requests without an {@code Idempotency-Key} header (controller
 *       {@code @NotBlank} validation handles the 400).</li>
 *   <li>Read and cache the request body so it is replayable for Spring's
 *       message converters after this filter has consumed the input stream.</li>
 *   <li>Compute a canonical body hash (sorted-keys JSON → SHA-256).</li>
 *   <li>Look up the storage key in {@link IdempotencyStore}:
 *       <ul>
 *         <li>Hit + same hash → replay cached response.</li>
 *         <li>Hit + different hash → 409 DUPLICATE_REQUEST.</li>
 *         <li>Miss → try to acquire a lock; 503 if lock held.</li>
 *       </ul>
 *   </li>
 *   <li>Proceed with the chain, cache a 2xx response, always release the lock.</li>
 * </ol>
 *
 * <p>The filter is registered via {@code IdempotencyConfig} at order
 * {@code HIGHEST_PRECEDENCE + 20} — after Spring Security, before
 * DispatcherServlet.
 */
public class InboundIdempotencyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InboundIdempotencyFilter.class);

    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    static final String INBOUND_API_PREFIX = "/api/v1/inbound/";
    static final String WEBHOOK_PREFIX = "/webhooks/";
    static final Duration LOCK_TTL = Duration.ofSeconds(30);
    static final Duration ENTRY_TTL = Duration.ofHours(24);

    private final IdempotencyStore idempotencyStore;
    private final ObjectMapper objectMapper;

    public InboundIdempotencyFilter(IdempotencyStore idempotencyStore, ObjectMapper objectMapper) {
        this.idempotencyStore = idempotencyStore;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // Step 1: skip if not a POST to /api/v1/inbound/ OR path is /webhooks/**
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

        // Step 3: cache the request body so it can be re-read by DispatcherServlet
        CachedBodyHttpServletRequestWrapper cachedRequest;
        try {
            cachedRequest = new CachedBodyHttpServletRequestWrapper(request);
        } catch (IOException e) {
            log.warn("Idempotency filter: failed to cache request body — skipping idempotency", e);
            filterChain.doFilter(request, response);
            return;
        }

        // Step 4: compute request body hash
        byte[] bodyBytes = cachedRequest.getCachedBody();
        String requestBodyHash = BodyHashUtil.computeHash(bodyBytes, objectMapper);

        // Step 5: build storage key: POST:{sha256(requestURI)}:{idempotencyKey}
        String requestUriHash = BodyHashUtil.sha256hex(
                request.getRequestURI().getBytes(StandardCharsets.UTF_8));
        String storageKey = "POST:" + requestUriHash + ":" + idempotencyKey;

        // Step 6: lookup
        Optional<StoredResponse> stored;
        try {
            stored = idempotencyStore.lookup(storageKey);
        } catch (Exception e) {
            log.warn("Idempotency filter: store lookup failed — proceeding without idempotency check", e);
            filterChain.doFilter(cachedRequest, response);
            return;
        }

        if (stored.isPresent()) {
            StoredResponse entry = stored.get();
            if (requestBodyHash.equals(entry.requestHash())) {
                // Same key + same body → replay
                replayResponse(response, entry);
                return;
            } else {
                // Same key + different body → 409 DUPLICATE_REQUEST
                writeDuplicateRequestError(response);
                return;
            }
        }

        // Step 7: not present — try to acquire lock
        boolean lockAcquired;
        try {
            lockAcquired = idempotencyStore.tryAcquireLock(storageKey, LOCK_TTL);
        } catch (Exception e) {
            log.warn("Idempotency filter: lock acquisition failed — proceeding without idempotency", e);
            filterChain.doFilter(cachedRequest, response);
            return;
        }

        if (!lockAcquired) {
            // Lock held by a concurrent request → 503
            writeProcessingError(response);
            return;
        }

        // Step 8 + 9: proceed with chain, cache 2xx, always release lock
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
                }
            }
            try {
                idempotencyStore.releaseLock(storageKey);
            } catch (Exception e) {
                log.warn("Idempotency filter: failed to release lock for key={}", storageKey, e);
            }
            // Step 10: flush cached bytes to real response
            responseWrapper.copyBodyToResponse();
        }
    }

    private boolean shouldApply(HttpServletRequest request) {
        if (!HttpMethod.POST.name().equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        if (path.startsWith(WEBHOOK_PREFIX)) {
            return false;
        }
        return path.startsWith(INBOUND_API_PREFIX);
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
}
