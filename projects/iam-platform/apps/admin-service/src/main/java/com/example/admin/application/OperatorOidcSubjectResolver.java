package com.example.admin.application;

import com.example.admin.application.port.AdminOperatorPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * The single, shared OIDC-subject → operator resolver.
 *
 * <p><b>TASK-MONO-299 (ADR-MONO-040 Phase 3 part B) — account_id-only.</b> The
 * transitional DUAL-KEY (account_id first, legacy email fallback) introduced in
 * Phase 2 (TASK-MONO-295) is removed now that the part-A email→account_id backfill
 * (TASK-MONO-298) has migrated {@code admin_operators.oidc_subject} to the account
 * UUID in every demo/e2e seed (and the operational backfill-before-deploy
 * prerequisite covers real deployments). The operator is resolved by the account_id
 * {@code oidcSubject} ONLY — the SAS access-token {@code sub} (= account UUID,
 * jwt-standard-claims.md). A row that does not match resolves to empty (the caller
 * fail-closes — 401 / not-assigned, never leaking operator existence).
 *
 * <p><b>Why a shared component</b>: BOTH operator-token exchanges resolve the operator
 * by the subject token's {@code sub} and must apply the IDENTICAL resolution —
 * the assume-tenant exchange ({@link OperatorAssignmentCheckUseCase}, auth-service →
 * admin {@code /internal/operator-assignments/check}) AND the login-time exchange
 * ({@link TokenExchangeService}, {@code POST /api/admin/auth/token-exchange}). Centralising
 * the resolution here means a THIRD sub-keyed path cannot silently miss the migration.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperatorOidcSubjectResolver {

    private final AdminOperatorPort operatorPort;

    /**
     * Resolve the {@code admin_operators} row for an OIDC subject (account_id-only).
     *
     * @param oidcSubject the operator's GAP OIDC {@code sub} (account_id — the
     *                    account UUID; jwt-standard-claims.md {@code sub})
     * @return the resolved operator row, or empty when no row matches (the caller
     *         fail-closes — 401 / not-assigned)
     */
    public Optional<AdminOperatorPort.OperatorView> resolve(String oidcSubject) {
        if (oidcSubject == null || oidcSubject.isBlank()) {
            return Optional.empty();
        }
        return operatorPort.findByOidcSubject(oidcSubject);
    }
}
