package com.example.account.application.port;

import java.util.List;

/**
 * TASK-BE-386 (ADR-MONO-036 P4, M4): platform-level (cross-tenant) read of the
 * {@code account_id → identity_id} bindings that account_db has resolved, used to
 * drive the auth_db credential identity backfill.
 *
 * <p>This is deliberately a SEPARATE port from the tenant-scoped
 * {@code AccountRepository} (whose contract forbids non-tenant-scoped lookups to
 * prevent cross-tenant leaks — multi-tenancy.md § Repository level). A bulk
 * reconciliation is a platform operation that legitimately spans all tenants, so it
 * lives on its own clearly-named port (mirrors how cross-tenant batch jobs read via
 * the infrastructure repository, e.g. {@code AccountDormantScheduler}).</p>
 */
public interface AccountIdentityBindingReader {

    /**
     * @return every account that already carries a non-null {@code identity_id}
     *         (after the V0024 account_db backfill), as propagation pairs. Accounts
     *         still unlinked (mint failed / no same-origin identity) are excluded.
     */
    List<AuthServicePort.CredentialIdentityBinding> findLinkedBindings();
}
