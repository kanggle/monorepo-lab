package com.kanggle.platformconsole.bff.adapter.inbound.web;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-MONO-421 — the catch-all {@code @ExceptionHandler(Exception.class)} used to swallow
 * Spring's 405/415 framework exceptions into a 500. These pin the dedicated handlers.
 */
@DisplayName("GlobalExceptionHandler — 405/415 framework errors")
class GlobalExceptionHandlerNotFoundTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("HttpRequestMethodNotSupportedException — 405, METHOD_NOT_ALLOWED, Allow header")
    void handleMethodNotSupported_returns405WithAllowHeader() {
        HttpRequestMethodNotSupportedException ex =
                new HttpRequestMethodNotSupportedException("DELETE", List.of("GET", "POST"));

        ResponseEntity<ObjectNode> response = handler.handleMethodNotSupported(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody().get("code").asText()).isEqualTo("METHOD_NOT_ALLOWED");
        assertThat(response.getHeaders().getAllow())
                .containsExactlyInAnyOrder(HttpMethod.GET, HttpMethod.POST);
    }

    @Test
    @DisplayName("HttpMediaTypeNotSupportedException — 415, UNSUPPORTED_MEDIA_TYPE")
    void handleMediaTypeNotSupported_returns415() {
        HttpMediaTypeNotSupportedException ex = new HttpMediaTypeNotSupportedException(
                MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<ObjectNode> response = handler.handleMediaTypeNotSupported(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody().get("code").asText()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
    }
}
