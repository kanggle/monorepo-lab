package com.example.admin.application;

public record LockAccountCommand(
        String accountId,
        String reason,
        String ticketId,
        String idempotencyKey,
        OperatorContext operator
) {}
