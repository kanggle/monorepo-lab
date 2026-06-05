package com.example.admin.application;

import java.util.List;

public record BulkLockAccountCommand(
        List<String> accountIds,
        String reason,
        String ticketId,
        String idempotencyKey,
        OperatorContext operator
) {}
