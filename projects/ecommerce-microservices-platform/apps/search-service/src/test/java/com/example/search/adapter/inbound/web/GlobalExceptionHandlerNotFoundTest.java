package com.example.search.adapter.inbound.web;

import com.example.web.dto.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GlobalExceptionHandler 매핑 없는 경로(404) 단위 테스트")
class GlobalExceptionHandlerNotFoundTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("NoResourceFoundException(매핑 없는 경로)이 404 NOT_FOUND로 처리된다 (500 아님)")
    void handleNoResourceFound_returns404NotFound() throws Exception {
        NoResourceFoundException ex =
                new NoResourceFoundException(HttpMethod.GET, "/api/definitely-not-a-real-endpoint");

        ErrorResponse body = handler.handleNoResourceFound(ex);

        assertThat(body.code()).isEqualTo("NOT_FOUND");
        assertThat(body.message()).isEqualTo("The requested resource was not found");

        Method m = GlobalExceptionHandler.class.getMethod("handleNoResourceFound", NoResourceFoundException.class);
        assertThat(m.getAnnotation(ResponseStatus.class).value()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("NoHandlerFoundException(매핑 없는 경로)이 404 NOT_FOUND로 처리된다 (500 아님)")
    void handleNoHandlerFound_returns404NotFound() throws Exception {
        NoHandlerFoundException ex =
                new NoHandlerFoundException("GET", "/api/definitely-not-a-real-endpoint", new HttpHeaders());

        ErrorResponse body = handler.handleNoHandlerFound(ex);

        assertThat(body.code()).isEqualTo("NOT_FOUND");
        assertThat(body.message()).isEqualTo("The requested resource was not found");

        Method m = GlobalExceptionHandler.class.getMethod("handleNoHandlerFound", NoHandlerFoundException.class);
        assertThat(m.getAnnotation(ResponseStatus.class).value()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("HttpRequestMethodNotSupportedException(잘못된 HTTP 메서드)이 405 METHOD_NOT_ALLOWED로 처리된다 (500 아님)")
    void handleMethodNotSupported_returns405MethodNotAllowed() {
        HttpRequestMethodNotSupportedException ex =
                new HttpRequestMethodNotSupportedException("DELETE", List.of("GET", "POST"));

        ResponseEntity<ErrorResponse> response = handler.handleMethodNotSupported(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("METHOD_NOT_ALLOWED");
        assertThat(response.getHeaders().getAllow()).contains(HttpMethod.GET, HttpMethod.POST);
    }

    @Test
    @DisplayName("HttpMediaTypeNotSupportedException(잘못된 Content-Type)이 415 UNSUPPORTED_MEDIA_TYPE으로 처리된다 (500 아님)")
    void handleMediaTypeNotSupported_returns415UnsupportedMediaType() throws Exception {
        HttpMediaTypeNotSupportedException ex =
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON));

        ErrorResponse body = handler.handleMediaTypeNotSupported(ex);

        assertThat(body.code()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");

        Method m = GlobalExceptionHandler.class.getMethod("handleMediaTypeNotSupported", HttpMediaTypeNotSupportedException.class);
        assertThat(m.getAnnotation(ResponseStatus.class).value()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }
}
