package com.example.admin.application;

public record RevokeSessionCommand(
        String accountId,
        String reason,
        String idempotencyKey,
        OperatorContext operator
) {}
