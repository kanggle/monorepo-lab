package com.kanggle.platformconsole.bff.adapter.inbound.web;

import com.example.web.dto.ErrorResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * TASK-MONO-421 originally pinned dedicated 405/415 handlers so Spring's framework exceptions do
 * not fall into the catch-all 500. TASK-PC-BE-016 (ADR-MONO-058 § D2) moved the generic
 * (non-domain) tail — 405, 415, unmapped-route 404, and the catch-all 500 — onto the shared
 * {@code libs/java-web-servlet} {@code CommonGlobalExceptionHandler}; {@link GlobalExceptionHandler}
 * now inherits these methods instead of declaring them locally. These tests exercise the
 * <b>inherited</b> methods directly (return type moved from this class's local {@code ObjectNode}
 * builder to the shared {@link ErrorResponse} record — see class Javadoc) and pin the
 * {@code {code, message, timestamp}} wire shape (platform/error-handling.md) byte-for-byte, so a
 * future change to the shared handler cannot silently drop a field this contract requires.
 */
@DisplayName("GlobalExceptionHandler — generic (non-domain) tail: 405 / 415 / 404 / 500")
class GlobalExceptionHandlerNotFoundTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("HttpRequestMethodNotSupportedException — 405, METHOD_NOT_ALLOWED, Allow header")
    void handleMethodNotSupported_returns405WithAllowHeader() {
        HttpRequestMethodNotSupportedException ex =
                new HttpRequestMethodNotSupportedException("DELETE", List.of("GET", "POST"));

        ResponseEntity<ErrorResponse> response = handler.handleMethodNotSupported(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody().code()).isEqualTo("METHOD_NOT_ALLOWED");
        assertThat(response.getHeaders().getAllow())
                .containsExactlyInAnyOrder(HttpMethod.GET, HttpMethod.POST);
        assertWireShape(response.getBody());
    }

    @Test
    @DisplayName("HttpMediaTypeNotSupportedException — 415, UNSUPPORTED_MEDIA_TYPE (500 아님)")
    void handleMediaTypeNotSupported_returns415() {
        HttpMediaTypeNotSupportedException ex = new HttpMediaTypeNotSupportedException(
                MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<ErrorResponse> response = handler.handleMediaTypeNotSupported(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody().code()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
        assertWireShape(response.getBody());
    }

    @Test
    @DisplayName("Exception — 500, INTERNAL_ERROR (catch-all, formerly local handleGeneric)")
    void handleGeneral_returns500() {
        Exception ex = new RuntimeException("unexpected failure");

        ResponseEntity<ErrorResponse> response = handler.handleGeneral(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertWireShape(response.getBody());
    }

    @Test
    @DisplayName("NoResourceFoundException — 404, NOT_FOUND (net-new: previously fell into the "
            + "local 500 catch-all, since this class never declared this arm)")
    void handleNoResourceFound_returns404() {
        NoResourceFoundException ex =
                new NoResourceFoundException(HttpMethod.GET, "/api/definitely-not-a-real-endpoint");

        ResponseEntity<ErrorResponse> response = handler.handleNoResourceFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
        assertWireShape(response.getBody());
    }

    /**
     * Contract test (AC): {@code {code, message, timestamp}} — exactly these three fields,
     * {@code timestamp} a parseable ISO-8601 instant — for the generic tail, unchanged in shape by
     * the {@code ObjectNode} → {@link ErrorResponse} return-type swap this adoption required.
     */
    private void assertWireShape(ErrorResponse body) {
        assertThat(body).isNotNull();
        JsonNode json = objectMapper.valueToTree(body);
        Iterable<String> fieldNames = json::fieldNames;
        assertThat(fieldNames).containsExactlyInAnyOrder("code", "message", "timestamp");
        assertThat(json.get("code").asText()).isNotBlank();
        assertThat(json.get("message")).isNotNull();
        assertThatCode(() -> Instant.parse(json.get("timestamp").asText()))
                .doesNotThrowAnyException();
    }
}
