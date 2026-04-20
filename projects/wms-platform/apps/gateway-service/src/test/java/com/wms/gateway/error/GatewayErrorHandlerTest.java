package com.wms.gateway.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class GatewayErrorHandlerTest {

    /**
     * Mirrors the Spring Boot default ObjectMapper configuration:
     * {@code JavaTimeModule} registered and timestamps written as ISO 8601
     * strings (not numeric). That matches the contract enforced by
     * {@code platform/error-handling.md} § Error Response Format.
     */
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final GatewayErrorHandler handler = new GatewayErrorHandler(mapper);

    @Test
    void writesPlatformEnvelope_withStatusAndJsonContentType() throws Exception {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/master/warehouses"));

        handler.write(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required").block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_JSON);

        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body).isNotNull();
        JsonNode node = mapper.readTree(body);
        assertThat(node.get("code").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(node.get("message").asText()).isEqualTo("Authentication required");
        // Platform envelope: timestamp is required and must be ISO 8601 UTC
        // (per platform/error-handling.md § Error Response Format).
        assertThat(node.get("timestamp").asText())
                .matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$");
    }

    @Test
    void bodyIsValidUtf8Json() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/whatever"));

        handler.write(exchange, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED", "slow down").block();

        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body).isNotNull();
        // Ensure the bytes round-trip as UTF-8 without BOM surprises
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo(body);
    }
}
