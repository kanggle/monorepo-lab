package com.example.user.presentation.dto.response;

import com.example.user.application.result.UserCountSummaryResult;

public record UserCountSummaryResponse(
        long today,
        long week,
        long month,
        long total
) {
    public static UserCountSummaryResponse from(UserCountSummaryResult result) {
        return new UserCountSummaryResponse(
                result.today(),
                result.week(),
                result.month(),
                result.total()
        );
    }
}
