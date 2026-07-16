package com.example.fanplatform.notification.presentation.advice;

import com.example.fanplatform.notification.presentation.dto.ApiErrorBody;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-MONO-420: an unmapped path must resolve to 404, not the catch-all 500
 * in {@link AbstractDomainExceptionHandler}. Mirrors ecommerce order-service's
 * {@code GlobalExceptionHandlerUnitTest} (EC-504).
 */
@DisplayName("GlobalExceptionHandler 404 처리 단위 테스트")
class GlobalExceptionHandlerNotFoundTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

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

    @Test
    @DisplayName("HttpRequestMethodNotSupportedException(지원하지 않는 HTTP 메서드)이 405 METHOD_NOT_ALLOWED로 처리된다 (500 아님)")
    void handleMethodNotSupported_returns405MethodNotAllowed() {
        HttpRequestMethodNotSupportedException ex =
                new HttpRequestMethodNotSupportedException("DELETE", List.of("GET", "POST"));

        ResponseEntity<ApiErrorBody> result = handler.handleMethodNotSupported(ex);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().code()).isEqualTo("METHOD_NOT_ALLOWED");
        assertThat(result.getHeaders().getAllow()).contains(HttpMethod.GET, HttpMethod.POST);
    }

    @Test
    @DisplayName("HttpMediaTypeNotSupportedException(지원하지 않는 Content-Type)이 415 UNSUPPORTED_MEDIA_TYPE로 처리된다 (500 아님)")
    void handleMediaTypeNotSupported_returns415UnsupportedMediaType() {
        HttpMediaTypeNotSupportedException ex =
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<ApiErrorBody> result = handler.handleMediaTypeNotSupported(ex);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().code()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
    }
}
