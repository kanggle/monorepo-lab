package com.example.promotion.interfaces.rest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record ApplyCouponRequest(
        @NotBlank(message = "주문 ID는 필수입니다")
        String orderId,

        @Positive(message = "주문 금액은 양수여야 합니다")
        long orderAmount
) {
}
