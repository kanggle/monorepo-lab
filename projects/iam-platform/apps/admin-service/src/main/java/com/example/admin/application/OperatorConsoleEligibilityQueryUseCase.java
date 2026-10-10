package com.example.admin.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * TASK-MONO-772 S4 (ADR-MONO-080 D6; auth-to-admin.md § GET /internal/operators/console-eligibility) — may this
 * consumer-pool account receive a {@code platform-console-web} token? I.e. does it have a <b>live</b> operator
 * facet.
 *
 * <p>The predicate is <b>the operator token exchange's</b> ({@link TokenExchangeService}): the operator resolved
 * by {@code oidc_subject = accountId} through the same shared {@link OperatorOidcSubjectResolver}, and
 * {@code status = ACTIVE}. Sharing the resolver means the issuer's answer and the exchange's answer cannot drift:
 * a console token is minted exactly for the subjects the exchange would then accept.
 *
 * <p>Deliberately NOT {@link OperatorFacetQueryUseCase}: that read is status-blind and two-axis (it answers «may
 * this account be moved into the pool»). Using it here would hand a console token to a suspended operator, whose
 * next exchange is {@code 401} → the console's {@code /onboarding} (772 AC-3). The identity axis is not asked
 * either — the exchange resolves by {@code sub} only.
 *
 * <p>Read-only — no {@code admin_actions} row. Answers a single boolean; nothing else about the operator leaks.
 */
@Service
@RequiredArgsConstructor
public class OperatorConsoleEligibilityQueryUseCase {

    /** {@code admin_operators.status} the exchange admits — the literal {@link TokenExchangeService} compares. */
    static final String ACTIVE = "ACTIVE";

    private final OperatorOidcSubjectResolver operatorResolver;

    @Transactional(readOnly = true)
    public boolean isConsoleEligible(String accountId) {
        return operatorResolver.resolve(accountId)
                .map(operator -> ACTIVE.equals(operator.status()))
                .orElse(false);
    }
}
