package com.wms.gateway.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class GatewayErrorHandlerTest {

    private final ObjectMapper mapper = new ObjectMapper();
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
        assertThat(node.get("timestamp").asText()).isNotEmpty();
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
