package com.example.promotion.interfaces.rest.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record IssueCouponsRequest(
        @NotEmpty(message = "사용자 ID 목록은 필수입니다")
        List<String> userIds
) {
}
