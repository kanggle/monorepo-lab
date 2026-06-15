package com.example.account.presentation.dto.response;

import com.example.account.application.service.CredentialIdentityBackfillUseCase;

/**
 * TASK-BE-386 (ADR-MONO-036 P4, M4): result of the credential identity backfill sweep.
 *
 * @param accountsScanned   linked accounts found in account_db (propagation candidates)
 * @param credentialsUpdated credential rows that received an identity_id in auth_db
 *                           (already-linked / missing credentials count 0 — idempotent)
 */
public record IdentityBackfillResponse(int accountsScanned, int credentialsUpdated) {

    public static IdentityBackfillResponse from(CredentialIdentityBackfillUseCase.Result result) {
        return new IdentityBackfillResponse(result.accountsScanned(), result.credentialsUpdated());
    }
}
