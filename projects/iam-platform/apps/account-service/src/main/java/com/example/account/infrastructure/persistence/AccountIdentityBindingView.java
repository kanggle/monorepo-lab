package com.example.account.infrastructure.persistence;

/**
 * TASK-BE-386 (ADR-MONO-036 P4, M4): Spring Data projection for the cross-tenant
 * {@code account_id → identity_id} read that drives the credential identity backfill.
 *
 * <p>Read via a native projection ({@code SELECT id AS accountId, identity_id AS identityId})
 * so {@code identity_id} stays UNMAPPED on {@link AccountJpaEntity} (the merge-overwrite
 * hazard). Column aliases match the getter names.</p>
 */
public interface AccountIdentityBindingView {

    String getAccountId();

    String getIdentityId();
}
