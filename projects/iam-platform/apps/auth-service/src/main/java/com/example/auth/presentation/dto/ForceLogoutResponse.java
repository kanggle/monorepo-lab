package com.example.auth.presentation.dto;

import com.example.auth.application.ForceLogoutUseCase;

import java.time.Instant;

public record ForceLogoutResponse(String accountId, int revokedTokenCount, Instant revokedAt) {

    public static ForceLogoutResponse from(ForceLogoutUseCase.Result result) {
        return new ForceLogoutResponse(result.accountId(), result.revokedTokenCount(), result.revokedAt());
    }
}
