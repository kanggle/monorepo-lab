package com.example.product.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateVariantRequest(
        @NotBlank(message = "옵션명은 필수입니다") String optionName,
        @PositiveOrZero(message = "추가 가격은 0 이상이어야 합니다") long additionalPrice
) {}
