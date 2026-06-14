package com.example.account.presentation.dto.response;

import com.example.account.application.service.ResolveOrCreateIdentityUseCase.Outcome;
import com.example.account.application.service.ResolveOrCreateIdentityUseCase.ResolveOrCreateIdentityResult;

/**
 * TASK-BE-374 (ADR-MONO-034 U4 / U6 step 3d): response DTO for
 * {@code POST /internal/tenants/{tenantId}/identities:resolveOrCreate}.
 *
 * <p>{@code identityId} is the central identity for the (tenant, email):
 * non-null when an identity was {@code CREATED} or {@code REUSED}; {@code null}
 * when {@code EXISTS_NOT_REUSED} (an identity exists but the caller did NOT opt in
 * — no link, no merge per ADR-034 U3). The caller (admin-service
 * {@code CreateOperatorUseCase}) links only on a non-null id.
 */
public record ResolveOrCreateIdentityResponse(
        String identityId,
        String outcome
) {
    public static ResolveOrCreateIdentityResponse from(ResolveOrCreateIdentityResult result) {
        return new ResolveOrCreateIdentityResponse(result.identityId(), result.outcome().name());
    }

    public static ResolveOrCreateIdentityResponse of(String identityId, Outcome outcome) {
        return new ResolveOrCreateIdentityResponse(identityId, outcome.name());
    }
}
