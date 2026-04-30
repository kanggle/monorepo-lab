package com.example.admin.application;

public record GdprDeleteCommand(
        String accountId,
        String reason,
        String ticketId,
        String idempotencyKey,
        OperatorContext operator
) {}
