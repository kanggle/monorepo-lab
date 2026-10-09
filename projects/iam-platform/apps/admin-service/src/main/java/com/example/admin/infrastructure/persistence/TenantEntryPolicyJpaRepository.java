package com.example.admin.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** TASK-MONO-771 — Spring Data repository for {@link TenantEntryPolicyJpaEntity}. */
public interface TenantEntryPolicyJpaRepository extends JpaRepository<TenantEntryPolicyJpaEntity, String> {

    /** The tenants among {@code tenantIds} whose entry policy is ON — one {@code IN} read. */
    @Query("SELECT p.tenantId FROM TenantEntryPolicyJpaEntity p "
            + "WHERE p.tenantId IN :tenantIds AND p.requireMfa = true")
    List<String> findTenantIdsRequiringMfa(@Param("tenantIds") Collection<String> tenantIds);
}
