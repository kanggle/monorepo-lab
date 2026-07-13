package com.example.account.application.command;

/**
 * TASK-BE-507: {@code tenantId} is the tenant the account is born into — resolved by
 * auth-service from the initiating OIDC client (a web-store client yields {@code ecommerce}).
 * {@code null} means no tenant reached us and pins to {@code fan-platform} (net-zero).
 */
public record SignupCommand(
        String email,
        String password,
        String displayName,
        String locale,
        String timezone,
        String tenantId
) {
}
