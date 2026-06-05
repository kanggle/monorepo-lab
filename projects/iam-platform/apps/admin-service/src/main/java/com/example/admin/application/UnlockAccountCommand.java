package com.example.admin.application;

public record UnlockAccountCommand(
        String accountId,
        String reason,
        String ticketId,
        String idempotencyKey,
        OperatorContext operator
) {}
