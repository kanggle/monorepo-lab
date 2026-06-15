package com.wms.outbound.adapter.in.web.filter;

import com.example.web.idempotency.IdempotencyErrorWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.outbound.adapter.in.web.dto.response.ApiErrorEnvelope;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;

/**
 * Writes the outbound-service idempotency error responses with the service's own
 * {@link ApiErrorEnvelope}, keeping the error contract service-owned
 * (ADR-MONO-038 I4). Byte-compatible with the responses the former
 * {@code OutboundIdempotencyFilter} emitted, including the 400 over-length-key
 * guard ({@code outbound-service} enforces a 255-char Idempotency-Key limit).
 */
public class OutboundIdempotencyErrorWriter implements IdempotencyErrorWriter {

    private final ObjectMapper objectMapper;

    public OutboundIdempotencyErrorWriter(ObjectMapper objectMapper) {
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
