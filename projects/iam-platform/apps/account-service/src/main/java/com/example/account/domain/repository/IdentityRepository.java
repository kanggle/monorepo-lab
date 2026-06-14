package com.example.account.domain.repository;

import com.example.account.domain.identity.Identity;
import com.example.account.domain.tenant.TenantId;

import java.util.Optional;

/**
 * Repository for the central identity registry (ADR-MONO-034 U1-A).
 *
 * <p>Tenant-scoped, mirroring {@link AccountRepository}: an identity belongs to
 * exactly one tenant in step 3a (the backfill is 1:1 with the tenant-scoped
 * accounts). Lookups take the tenant to prevent cross-tenant leaks.
 */
public interface IdentityRepository {

    Identity save(Identity identity);

    Optional<Identity> findById(String identityId);

    /**
     * Find the identity for the given (tenant, email). Email-match is the
     * NECESSARY-but-not-sufficient pre-condition the opt-in link surface (ADR-034
     * U3, step 3c) will build on — it never auto-links on a match.
     */
    Optional<Identity> findByTenantAndEmail(TenantId tenantId, String email);
}
