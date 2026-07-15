package com.example.scmplatform.demandplanning.adapter.inbound.web.advice;

import com.example.scmplatform.demandplanning.adapter.inbound.web.dto.ApiErrorBody;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-MONO-420: an unmapped path must return 404, not fall through the
 * catch-all {@code handleGeneral} to 500.
 */
class GlobalExceptionHandlerNotFoundTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("NoResourceFoundException(매핑 없는 경로)이 404 NOT_FOUND로 처리된다 (500 아님)")
    void handleNoResourceFound_returns404NotFound() {
        NoResourceFoundException ex =
                new NoResourceFoundException(HttpMethod.GET, "/api/definitely-not-a-real-endpoint");

        ResponseEntity<ApiErrorBody> result = handler.handleNoResourceFound(ex);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().code()).isEqualTo("NOT_FOUND");
        assertThat(result.getBody().message()).isEqualTo("The requested resource was not found");
    }

    @Test
    @DisplayName("NoHandlerFoundException(매핑 없는 경로)이 404 NOT_FOUND로 처리된다 (500 아님)")
    void handleNoHandlerFound_returns404NotFound() {
        NoHandlerFoundException ex =
                new NoHandlerFoundException("GET", "/api/definitely-not-a-real-endpoint", new HttpHeaders());

        ResponseEntity<ApiErrorBody> result = handler.handleNoHandlerFound(ex);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().code()).isEqualTo("NOT_FOUND");
        assertThat(result.getBody().message()).isEqualTo("The requested resource was not found");
    }
}
