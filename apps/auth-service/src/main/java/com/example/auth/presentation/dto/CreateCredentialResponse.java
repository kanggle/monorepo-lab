package com.example.auth.presentation.dto;

import com.example.auth.application.result.CreateCredentialResult;

import java.time.Instant;

public record CreateCredentialResponse(
        String accountId,
        Instant createdAt
) {
    public static CreateCredentialResponse from(CreateCredentialResult result) {
        return new CreateCredentialResponse(result.accountId(), result.createdAt());
    }
}
