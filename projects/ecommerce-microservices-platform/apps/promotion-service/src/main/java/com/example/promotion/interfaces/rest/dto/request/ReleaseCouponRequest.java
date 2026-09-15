package com.example.promotion.interfaces.rest.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ReleaseCouponRequest(
        @NotBlank(message = "주문 ID는 필수입니다")
        String orderId
) {
}
