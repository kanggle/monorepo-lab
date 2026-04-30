package com.example.auth.domain.repository;

import com.example.auth.domain.credentials.Credential;

import java.util.List;
import java.util.Optional;

/**
 * Port interface for credential persistence.
 */
public interface CredentialRepository {

    Optional<Credential> findByAccountId(String accountId);

    /**
     * Resolve a credential by login email within a specific tenant.
     * Introduced by TASK-BE-229 for tenant-aware login.
     *
     * @param tenantId the tenant to scope the lookup to
     * @param email    the login email (callers may pass raw input; implementations normalize)
     */
    Optional<Credential> findByTenantIdAndEmail(String tenantId, String email);

    /**
     * Resolve all credentials matching the given email across all tenants.
     * Used when no tenantId is supplied on login to detect multi-tenant ambiguity.
     *
     * @return empty list if no credential exists; 1-element list if unambiguous;
     *         2+ elements if multiple tenants have the same email
     */
    List<Credential> findAllByEmail(String email);

    /**
     * Resolve a credential by login email (legacy, single-tenant).
     * Implementations normalize the email (lower-case + trim) before querying.
     *
     * @deprecated Prefer {@link #findByTenantIdAndEmail(String, String)} for tenant-aware lookup.
     */
    @Deprecated
    Optional<Credential> findByAccountIdEmail(String email);

    Credential save(Credential credential);

    /**
     * Whether a credential row already exists for the given accountId. Used by
     * the internal credential-create endpoint to return 409 on duplicate before
     * attempting an insert (argon2id hashing is expensive, so we short-circuit).
     */
    boolean existsByAccountId(String accountId);
}
