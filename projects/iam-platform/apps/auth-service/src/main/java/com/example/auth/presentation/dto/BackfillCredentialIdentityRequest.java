package com.example.auth.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * TASK-BE-386 (ADR-MONO-036 P4, M4): batch request for the credential identity
 * backfill. account-service (which owns the account_id → identity_id mapping in
 * account_db) posts the pairs to be written onto {@code auth_db.credentials.identity_id}.
 *
 * <p>This is the cross-DB half of the production reconciliation: auth_db cannot read
 * account_db, so the mapping is pushed in. Each pair is applied with the M2
 * {@code assignIdentityId} writer (native, {@code IS NULL}-guarded, idempotent,
 * no overwrite — ADR-034 § 1.3). Same-origin propagation, NOT an email merge.</p>
 */
public record BackfillCredentialIdentityRequest(
        @NotEmpty @Valid List<Item> items) {

    /**
     * One (accountId, identityId) binding. {@code accountId} keys the credential row
     * (UNIQUE {@code credentials.account_id}); {@code identityId} is the central
     * identity the matching account already resolved to (account_db).
     */
    public record Item(
            @NotBlank @Size(max = 36) String accountId,
            @NotBlank @Size(max = 36) String identityId) {
    }
}
