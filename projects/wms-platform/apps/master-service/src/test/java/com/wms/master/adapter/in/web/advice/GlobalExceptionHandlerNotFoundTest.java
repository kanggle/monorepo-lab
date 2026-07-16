package com.wms.master.adapter.in.web.advice;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.master.adapter.in.web.dto.response.ApiErrorEnvelope;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Unit tests for {@link GlobalExceptionHandler}'s unmatched-path / wrong-method /
 * wrong-media-type handling (TASK-MONO-162, TASK-MONO-421): a request to a path
 * this service does not serve, or that uses the wrong HTTP method / Content-Type,
 * must degrade to the correct 4xx status, not fall through to the catch-all 500.
 */
class GlobalExceptionHandlerNotFoundTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void noResourceFound_returns404_withCode_NOT_FOUND() {
        NoResourceFoundException ex =
                new NoResourceFoundException(HttpMethod.GET, "/api/definitely-not-a-real-endpoint");

        ResponseEntity<ApiErrorEnvelope> resp = handler.handleNoResource(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody().error().code()).isEqualTo("NOT_FOUND");
        assertThat(resp.getBody().error().message()).isEqualTo("Resource not found");
    }

    @Test
    void methodNotSupported_returns405_withCode_METHOD_NOT_ALLOWED_andAllowHeader() {
        HttpRequestMethodNotSupportedException ex =
                new HttpRequestMethodNotSupportedException("DELETE", List.of("GET", "POST"));

        ResponseEntity<ApiErrorEnvelope> resp = handler.handleMethodNotSupported(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(resp.getBody().error().code()).isEqualTo("METHOD_NOT_ALLOWED");
        assertThat(resp.getHeaders().getAllow()).contains(HttpMethod.GET, HttpMethod.POST);
    }

    @Test
    void mediaTypeNotSupported_returns415_withCode_UNSUPPORTED_MEDIA_TYPE() {
        HttpMediaTypeNotSupportedException ex =
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<ApiErrorEnvelope> resp = handler.handleMediaTypeNotSupported(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(resp.getBody().error().code()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
    }
}
