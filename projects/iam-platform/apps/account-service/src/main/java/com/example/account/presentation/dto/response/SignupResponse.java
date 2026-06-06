package com.example.account.presentation.dto.response;

import com.example.account.application.result.SignupResult;

import java.time.Instant;

public record SignupResponse(
        String accountId,
        String email,
        String status,
        Instant createdAt
) {
    public static SignupResponse from(SignupResult result) {
        return new SignupResponse(
                result.accountId(),
                result.email(),
                result.status(),
                result.createdAt()
        );
    }
}
