package com.example.promotion.interfaces.rest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreatePromotionRequest(
        @NotBlank(message = "프로모션 이름은 필수입니다")
        String name,

        String description,

        @NotBlank(message = "할인 유형은 필수입니다")
        String discountType,

        @Positive(message = "할인 값은 양수여야 합니다")
        long discountValue,

        long maxDiscountAmount,

        @Positive(message = "최대 발급 수는 양수여야 합니다")
        int maxIssuanceCount,

        @NotNull(message = "시작일은 필수입니다")
        String startDate,

        @NotNull(message = "종료일은 필수입니다")
        String endDate
) {
}
