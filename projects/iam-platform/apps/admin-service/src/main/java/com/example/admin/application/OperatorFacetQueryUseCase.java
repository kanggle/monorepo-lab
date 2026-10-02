package com.example.admin.application;

import com.example.admin.application.port.AdminOperatorPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * TASK-BE-618 (ADR-MONO-078 A; auth-to-admin.md § GET /internal/operators/facet) — does this consumer
 * account carry an <b>operator facet</b>?
 *
 * <p>auth-service asks this before it moves a single-site account's credential into the consumer pool
 * (multi-tenancy.md § 소비자 계정 풀 § 3): an account with an operator facet is not moved in that step.
 * Two facets are recognised, both matched against ANY operator row whatever its status (a SUSPENDED
 * operator is still an operator):
 * <ul>
 *   <li>{@code admin_operators.oidc_subject = accountId} — an ADR-MONO-044 D5 self-onboarded operator,
 *       whose subject IS that consumer account ({@code TASK-MONO-746}).</li>
 *   <li>{@code admin_operators.identity_id = identityId} — the ADR-MONO-034 U3 operator identity link:
 *       moving the identity row into the pool would make that operator's identity a pool identity.</li>
 * </ul>
 *
 * <p>Read-only — no {@code admin_actions} row (same rule as the assignment check). The answer is a single
 * boolean: which axis matched is not disclosed.
 */
@Service
@RequiredArgsConstructor
public class OperatorFacetQueryUseCase {

    private final AdminOperatorPort adminOperatorPort;

    @Transactional(readOnly = true)
    public boolean isOperatorFaceted(String accountId, String identityId) {
        return adminOperatorPort.existsOperatorFacet(accountId, identityId);
    }
}
