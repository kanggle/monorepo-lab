package com.wms.gateway.error;

import com.fasterxml.jackson.core.io.JsonStringEncoder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Writes a platform-envelope error to the reactive response. Shared by the
 * security entry point, access-denied handler, and rate-limit rejection filter.
 */
public final class GatewayErrorHandler {

    private final ObjectMapper objectMapper;

    public GatewayErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes = serialize(ApiErrorEnvelope.of(code, message));
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    private byte[] serialize(ApiErrorEnvelope envelope) {
        try {
            return objectMapper.writeValueAsBytes(envelope);
        } catch (Exception e) {
            // Fallback to a hand-rolled JSON so an envelope always reaches the client.
            // Per platform/error-handling.md the envelope must always carry a
            // timestamp; use the current UTC instant so the fallback path stays
            // spec-compliant.
            //
            // Escape the code/message via Jackson's JsonStringEncoder so a
            // message containing quotes, backslashes, or control characters
            // cannot produce malformed JSON (TASK-BE-018 item 3).
            Instant timestamp = envelope.timestamp() != null ? envelope.timestamp() : Instant.now();
            JsonStringEncoder encoder = JsonStringEncoder.getInstance();
            String escapedCode = new String(encoder.quoteAsString(
                    envelope.code() != null ? envelope.code() : ""));
            String escapedMessage = new String(encoder.quoteAsString(
                    envelope.message() != null ? envelope.message() : ""));
            String escapedTimestamp = new String(encoder.quoteAsString(timestamp.toString()));
            String fallback = "{\"code\":\"" + escapedCode + "\",\"message\":\""
                    + escapedMessage + "\",\"timestamp\":\"" + escapedTimestamp + "\"}";
            return fallback.getBytes(StandardCharsets.UTF_8);
        }
    }
}
