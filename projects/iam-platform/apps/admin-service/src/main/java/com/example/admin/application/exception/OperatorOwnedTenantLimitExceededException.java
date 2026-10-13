package com.example.admin.application.exception;

/**
 * TASK-MONO-784 S5 (owner decision OD-1 · ADR-MONO-044 D4 · ADR-MONO-082 D4) — 422
 * {@code OPERATOR_OWNED_TENANT_LIMIT_EXCEEDED}: self-service onboarding by a caller whose existing operator row
 * already holds {@code TENANT_ADMIN} in as many distinct tenants as the per-identity cap
 * ({@code admin.onboarding.max-owned-tenants-per-operator}) allows. Raised BEFORE the tenant is created — nothing
 * exists to compensate (onboarding-api.md § 신원당 상한).
 */
public class OperatorOwnedTenantLimitExceededException extends RuntimeException {
    public OperatorOwnedTenantLimitExceededException(String message) {
        super(message);
    }
}
