package com.example.promotion.interfaces.rest.dto.response;

import com.example.promotion.application.result.IssueCouponsResult;

public record IssueCouponsResponse(int issuedCount) {

    public static IssueCouponsResponse from(IssueCouponsResult result) {
        return new IssueCouponsResponse(result.issuedCount());
    }
}
