package com.example.promotion.interfaces;

import com.example.promotion.domain.coupon.CouponRestoreNotAllowedException;
import com.example.promotion.interfaces.rest.controller.GlobalExceptionHandler;
import com.example.web.dto.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

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
}
