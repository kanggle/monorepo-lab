package com.example.admin.presentation.dto;

import com.example.admin.application.SelfServiceOnboardingUseCase;

import java.util.List;

/**
 * TASK-BE-474 (ADR-MONO-044) — response for a successful self-service onboarding:
 * the created tenant, the minted first-admin operator, and the roles it was granted
 * (scoped to the new tenant). The new tenant is born entitlement-empty (D6) — the
 * owner self-enables domain subscriptions afterward via their {@code TENANT_BILLING_ADMIN}.
 */
public record OnboardOrganizationResponse(
        String tenantId,
        String operatorId,
        List<String> roles,
        String status
) {
    public static OnboardOrganizationResponse from(SelfServiceOnboardingUseCase.Result r) {
        return new OnboardOrganizationResponse(r.tenantId(), r.operatorId(), r.roles(), "ACTIVE");
    }
}
