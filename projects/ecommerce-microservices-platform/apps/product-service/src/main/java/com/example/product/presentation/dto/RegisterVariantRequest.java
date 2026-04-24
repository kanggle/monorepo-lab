package com.example.product.presentation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record RegisterVariantRequest(
        @NotBlank(message = "옵션명은 필수입니다") String optionName,
        @Min(value = 0, message = "재고는 0 이상이어야 합니다") int stock,
        @Min(value = 0, message = "추가 금액은 0 이상이어야 합니다") long additionalPrice
) {}
