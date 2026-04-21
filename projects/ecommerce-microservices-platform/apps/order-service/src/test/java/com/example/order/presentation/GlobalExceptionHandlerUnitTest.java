package com.example.order.presentation;

import com.example.web.dto.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GlobalExceptionHandler 단위 테스트")
class GlobalExceptionHandlerUnitTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("ErrorResponse는 code, message, timestamp 필드만 포함한다")
    void errorResponse_containsCodeMessageTimestamp() {
        ErrorResponse response = ErrorResponse.of("TEST_CODE", "test message");

        assertThat(response.code()).isEqualTo("TEST_CODE");
        assertThat(response.message()).isEqualTo("test message");
        assertThat(response.timestamp()).isNotNull();
        assertThat(response.timestamp()).isNotBlank();
    }

    @Test
    @DisplayName("IllegalArgumentException 발생 시 400 Bad Request와 INVALID_ORDER_REQUEST 코드 반환")
    void handleIllegalArgument_returns400WithInvalidOrderRequest() {
        IllegalArgumentException ex = new IllegalArgumentException("수량은 1 이상이어야 합니다");

        ResponseEntity<ErrorResponse> result = handler.handleIllegalArgument(ex);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().code()).isEqualTo("INVALID_ORDER_REQUEST");
        assertThat(result.getBody().message()).isEqualTo("수량은 1 이상이어야 합니다");
        assertThat(result.getBody().timestamp()).isNotNull();
    }

    @Test
    @DisplayName("IllegalArgumentException 핸들러가 예외 메시지를 그대로 전달한다")
    void handleIllegalArgument_passesExceptionMessage() {
        String message = "상품명은 필수입니다";
        IllegalArgumentException ex = new IllegalArgumentException(message);

        ResponseEntity<ErrorResponse> result = handler.handleIllegalArgument(ex);

        assertThat(result.getBody().message()).isEqualTo(message);
    }

    @Test
    @DisplayName("OrderNotFoundException 핸들러가 ErrorResponse 형식으로 응답한다")
    void handleOrderNotFound_returnsErrorResponseFormat() {
        var ex = new com.example.order.domain.exception.OrderNotFoundException("order-1");

        ResponseEntity<ErrorResponse> result = handler.handleOrderNotFound(ex);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().code()).isEqualTo("ORDER_NOT_FOUND");
        assertThat(result.getBody().timestamp()).isNotNull();
    }

    @Test
    @DisplayName("UnauthorizedOrderAccessException 핸들러가 403 ACCESS_DENIED로 응답한다")
    void handleUnauthorized_returns403AccessDenied() {
        var ex = new com.example.order.application.exception.UnauthorizedOrderAccessException();

        ResponseEntity<ErrorResponse> result = handler.handleUnauthorized(ex);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().code()).isEqualTo("ACCESS_DENIED");
        assertThat(result.getBody().timestamp()).isNotNull();
    }

    @Test
    @DisplayName("OrderCannotBeCancelledException 핸들러가 ErrorResponse 형식으로 응답한다")
    void handleOrderCannotBeCancelled_returnsErrorResponseFormat() {
        var ex = new com.example.order.domain.exception.OrderCannotBeCancelledException("취소 불가");

        ResponseEntity<ErrorResponse> result = handler.handleOrderCannotBeCancelled(ex);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().code()).isEqualTo("ORDER_CANNOT_BE_CANCELLED");
        assertThat(result.getBody().timestamp()).isNotNull();
    }

    @Test
    @DisplayName("일반 Exception 핸들러가 ErrorResponse 형식으로 응답한다")
    void handleException_returnsErrorResponseFormat() {
        Exception ex = new RuntimeException("unexpected");

        ResponseEntity<ErrorResponse> result = handler.handleException(ex);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(result.getBody().timestamp()).isNotNull();
    }
}
