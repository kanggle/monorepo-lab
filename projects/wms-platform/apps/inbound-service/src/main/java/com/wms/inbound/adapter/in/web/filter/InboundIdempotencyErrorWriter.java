package com.wms.inbound.adapter.in.web.filter;

import com.example.web.idempotency.IdempotencyErrorWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.inbound.adapter.in.web.dto.response.ApiErrorEnvelope;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;

/**
 * Writes the inbound-service idempotency error responses with the service's own
 * {@link ApiErrorEnvelope}, keeping the error contract service-owned
 * (ADR-MONO-038 I4). Byte-compatible with the responses the former
 * {@code InboundIdempotencyFilter} emitted.
 *
 * <p>{@code inbound-service} has no Idempotency-Key length guard, so
 * {@code writeKeyTooLong} is never invoked (the interface default throws if it
 * were, signalling misconfiguration).
 */
public class InboundIdempotencyErrorWriter implements IdempotencyErrorWriter {

    private final ObjectMapper objectMapper;

    public InboundIdempotencyErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void writeConflict(HttpServletResponse response) throws IOException {
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
