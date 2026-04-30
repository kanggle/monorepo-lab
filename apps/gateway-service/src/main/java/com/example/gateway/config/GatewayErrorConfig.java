package com.example.gateway.config;

import com.example.web.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

/**
 * Global error handler for downstream service failures.
 * Converts connection/timeout errors into 503/504 responses.
 */
@Slf4j
@Configuration
public class GatewayErrorConfig {

    @Bean
    @Order(-1)
    public ErrorWebExceptionHandler gatewayErrorHandler(ObjectMapper objectMapper) {
        return (exchange, ex) -> {
            if (exchange.getResponse().isCommitted()) {
                return Mono.error(ex);
            }

            HttpStatus status;
            String code;
            String message;

            if (ex instanceof ResponseStatusException rse) {
                HttpStatus rseStatus = HttpStatus.resolve(rse.getStatusCode().value());
                if (rseStatus != null && rseStatus.is5xxServerError()) {
                    status = HttpStatus.SERVICE_UNAVAILABLE;
                    code = "SERVICE_UNAVAILABLE";
                    message = "Downstream service is temporarily unavailable";
                } else {
                    status = rseStatus != null ? rseStatus : HttpStatus.INTERNAL_SERVER_ERROR;
                    code = "INTERNAL_ERROR";
                    message = "Unexpected server-side error";
                }
            } else if (ex instanceof ConnectException || ex.getCause() instanceof ConnectException) {
                status = HttpStatus.SERVICE_UNAVAILABLE;
                code = "SERVICE_UNAVAILABLE";
                message = "Downstream service is temporarily unavailable";
            } else if (ex instanceof TimeoutException || ex.getCause() instanceof TimeoutException) {
                status = HttpStatus.GATEWAY_TIMEOUT;
                code = "GATEWAY_TIMEOUT";
                message = "Downstream service did not respond in time";
            } else {
                log.error("Unhandled gateway error: {}", ex.getMessage(), ex);
                status = HttpStatus.SERVICE_UNAVAILABLE;
                code = "SERVICE_UNAVAILABLE";
                message = "Downstream service is temporarily unavailable";
            }

            exchange.getResponse().setStatusCode(status);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

            ErrorResponse errorResponse = ErrorResponse.of(code, message);
            try {
                byte[] bytes = objectMapper.writeValueAsBytes(errorResponse);
                DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
                return exchange.getResponse().writeWith(Mono.just(buffer));
            } catch (Exception e) {
                String fallback = String.format(
                        "{\"code\":\"%s\",\"message\":\"%s\"}", code, message);
                DataBuffer buffer = exchange.getResponse().bufferFactory()
                        .wrap(fallback.getBytes(StandardCharsets.UTF_8));
                return exchange.getResponse().writeWith(Mono.just(buffer));
            }
        };
    }
}
