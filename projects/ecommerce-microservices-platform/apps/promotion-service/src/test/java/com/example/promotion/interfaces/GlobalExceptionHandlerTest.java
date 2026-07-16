package com.example.promotion.interfaces;

import com.example.promotion.domain.coupon.CouponRestoreNotAllowedException;
import com.example.promotion.interfaces.rest.controller.GlobalExceptionHandler;
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
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GlobalExceptionHandler 단위 테스트")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("CouponRestoreNotAllowedException 발생 시 422 상태 코드와 COUPON_RESTORE_NOT_ALLOWED 코드가 반환된다")
    void handleCouponRestoreNotAllowed_returns422WithErrorCode() {
        CouponRestoreNotAllowedException exception = new CouponRestoreNotAllowedException("coupon-1");

        ResponseEntity<ErrorResponse> response = handler.handleCouponRestoreNotAllowed(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("COUPON_RESTORE_NOT_ALLOWED");
        assertThat(response.getBody().message()).isEqualTo("Cannot restore expired coupon: coupon-1");
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    @DisplayName("CouponRestoreNotAllowedException은 500 INTERNAL_ERROR가 아닌 422로 처리된다")
    void handleCouponRestoreNotAllowed_notReturns500() {
        CouponRestoreNotAllowedException exception = new CouponRestoreNotAllowedException("coupon-99");

        ResponseEntity<ErrorResponse> response = handler.handleCouponRestoreNotAllowed(exception);

        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isNotEqualTo("INTERNAL_ERROR");
    }

    @Test
    @DisplayName("NoResourceFoundException(매핑 없는 경로)이 404 NOT_FOUND로 처리된다 (500 아님)")
    void handleNoResourceFound_returns404NotFound() {
        NoResourceFoundException exception =
                new NoResourceFoundException(HttpMethod.GET, "/api/definitely-not-a-real-endpoint");

        ResponseEntity<ErrorResponse> response = handler.handleNoResourceFound(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
        assertThat(response.getBody().message()).isEqualTo("The requested resource was not found");
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    @DisplayName("NoHandlerFoundException(매핑 없는 경로)이 404 NOT_FOUND로 처리된다 (500 아님)")
    void handleNoHandlerFound_returns404NotFound() {
        NoHandlerFoundException exception =
                new NoHandlerFoundException("GET", "/api/definitely-not-a-real-endpoint", new HttpHeaders());

        ResponseEntity<ErrorResponse> response = handler.handleNoHandlerFound(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
        assertThat(response.getBody().message()).isEqualTo("The requested resource was not found");
    }

    @Test
    @DisplayName("HttpRequestMethodNotSupportedException(잘못된 HTTP 메서드)이 405 METHOD_NOT_ALLOWED로 처리된다 (500 아님)")
    void handleMethodNotSupported_returns405MethodNotAllowed() {
        HttpRequestMethodNotSupportedException exception =
                new HttpRequestMethodNotSupportedException("DELETE", List.of("GET", "POST"));

        ResponseEntity<ErrorResponse> response = handler.handleMethodNotSupported(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("METHOD_NOT_ALLOWED");
        assertThat(response.getHeaders().getAllow()).contains(HttpMethod.GET, HttpMethod.POST);
    }

    @Test
    @DisplayName("HttpMediaTypeNotSupportedException(잘못된 Content-Type)이 415 UNSUPPORTED_MEDIA_TYPE으로 처리된다 (500 아님)")
    void handleMediaTypeNotSupported_returns415UnsupportedMediaType() {
        HttpMediaTypeNotSupportedException exception =
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<ErrorResponse> response = handler.handleMediaTypeNotSupported(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
    }
}
