package com.example.account.domain.repository;

import com.example.account.domain.tenant.Tenant;
import com.example.account.domain.tenant.TenantId;

import java.util.Optional;

/**
 * Port interface for tenant persistence.
 *
 * <p>Implemented by {@code TenantJpaAdapter} in the infrastructure layer.
 * The application layer depends only on this interface — never on JPA specifics.
 */
public interface TenantRepository {

    Optional<Tenant> findById(TenantId tenantId);

    /**
     * Returns {@code true} when a tenant with the given id exists and its status is ACTIVE.
     * Used for pre-condition checks before provisioning accounts into a tenant.
     */
    boolean existsActive(TenantId tenantId);
}
