package com.example.account.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * TASK-BE-381 (ADR-MONO-036 P1/P2, M1): born-unified identity mint helper for the
 * account-creating paths.
 *
 * <p>Mints (or reuses) the central {@code identities} row for a (tenant, email) at
 * account-creation time so a new account's {@code identity_id} is populated from
 * birth (ADR-MONO-032 D6-A for new records). Generalizes the operator-creation
 * pattern (ADR-MONO-034 U4) to the consumer registration paths.
 *
 * <p><b>Fail-soft (ADR-036 P2) via transaction ISOLATION.</b> The mint runs in its
 * OWN {@link Propagation#REQUIRES_NEW} transaction, so a mint failure (identity
 * infra unavailable, or {@link ResolveOrCreateIdentityUseCase}'s own
 * {@code @Transactional} boundary marking its physical tx rollback-only) is confined
 * to THIS transaction and does NOT poison the caller's registration transaction. The
 * caller wraps {@link #mintIdentity} in a try/catch and treats any failure as "born
 * unlinked" (identity_id stays NULL, reconciled later) — registration NEVER blocks on
 * the identity infrastructure (the ADR-034 registration-never-blocks availability
 * stance this preserves). Catching the exception in the CALLER (not here) is required:
 * the rollback-only mark lives on this REQUIRES_NEW physical tx and surfaces as the
 * commit-time exception the caller swallows.
 *
 * <p><b>Convergence (ADR-036 P1).</b> {@code reuseExisting=true}: when an identity
 * already exists for the (tenant, email) — e.g. an operator was provisioned first —
 * it is REUSED, so the consumer and operator sides converge on the SAME central
 * identity by construction (same-origin issuance via {@code uk_identities_tenant_email},
 * NOT an email auto-merge — ADR-034 § 1.3 no-silent-merge).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountIdentityProvisioner {

    private final ResolveOrCreateIdentityUseCase resolveOrCreateIdentityUseCase;

    /**
     * Mint or reuse the central identity for (tenantId, email) in an ISOLATED
     * transaction. Returns the resolved {@code identity_id} (never null on success —
     * {@code reuseExisting=true} always yields an identity).
     *
     * <p>Runs {@link Propagation#REQUIRES_NEW} so any failure here (including a
     * rollback-only mark from the inner {@code ResolveOrCreateIdentityUseCase}) is
     * confined to this physical transaction. The caller MUST invoke this in a
     * try/catch and fail-soft (born-unlinked) on any exception — never let a mint
     * failure abort account creation.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String mintIdentity(String tenantId, String email) {
        return resolveOrCreateIdentityUseCase.execute(tenantId, email, true).identityId();
    }
}
