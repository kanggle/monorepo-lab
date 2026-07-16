package com.wms.inventory.adapter.in.web.filter;

import com.example.web.idempotency.IdempotencyErrorWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.inventory.adapter.in.web.dto.response.ApiErrorEnvelope;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;

/**
 * Writes the inventory-service idempotency error responses with the service's own
 * {@link ApiErrorEnvelope}, keeping the error contract service-owned
 * (ADR-MONO-038 I4). Mirrors the bodies {@code GlobalExceptionHandler} emits for
 * the same codes (409 {@code DUPLICATE_REQUEST}, 400 {@code VALIDATION_ERROR}),
 * so a filter-emitted error is indistinguishable from a handler-emitted one.
 *
 * <p>The {@code inventory.idempotency.mismatch.count} counter
 * (specs/services/inventory-service/idempotency.md §5) is incremented here, on
 * {@link #writeConflict}, rather than from {@code recordLookup(RESULT_CONFLICT)}:
 * the filter's {@code RESULT_CONFLICT} tag also covers the lock-held 503 path
 * ({@link #writeProcessing}), which is an availability event, not the
 * "usually a bug" body mismatch the spec's counter names. Counting it in
 * {@code writeConflict} keeps the counter to genuine same-key/different-body 409s.
 */
public class InventoryIdempotencyErrorWriter implements IdempotencyErrorWriter {

    static final String METRIC_MISMATCH_COUNT = "inventory.idempotency.mismatch.count";

    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public InventoryIdempotencyErrorWriter(ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void writeConflict(HttpServletResponse response) throws IOException {
        if (meterRegistry != null) {
            meterRegistry.counter(METRIC_MISMATCH_COUNT).increment();
        }
        writeJsonError(response, HttpServletResponse.SC_CONFLICT, ApiErrorEnvelope.of(
                "DUPLICATE_REQUEST",
                "Idempotency-Key already used with a different request body"));
    }

    @Override
    public void writeProcessing(HttpServletResponse response) throws IOException {
        response.setHeader("Retry-After", "1");
        writeJsonError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, ApiErrorEnvelope.of(
                "PROCESSING",
                "Request is being processed"));
    }

    @Override
    public void writeKeyTooLong(HttpServletResponse response, int maxKeyLength) throws IOException {
        writeJsonError(response, HttpServletResponse.SC_BAD_REQUEST, ApiErrorEnvelope.of(
                "VALIDATION_ERROR",
                "Idempotency-Key header exceeds " + maxKeyLength + " characters"));
    }

    private void writeJsonError(HttpServletResponse response, int status, ApiErrorEnvelope envelope)
            throws IOException {
        byte[] body = objectMapper.writeValueAsBytes(envelope);
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
        response.getOutputStream().flush();
    }
}
