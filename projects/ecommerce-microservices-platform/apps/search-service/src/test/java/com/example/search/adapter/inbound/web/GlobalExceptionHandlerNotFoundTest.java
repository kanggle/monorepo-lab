package com.example.search.adapter.inbound.web;

import com.example.web.dto.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.lang.reflect.Method;

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
}
