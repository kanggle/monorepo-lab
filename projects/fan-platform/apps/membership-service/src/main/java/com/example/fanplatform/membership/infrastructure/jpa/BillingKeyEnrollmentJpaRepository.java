package com.example.fanplatform.membership.infrastructure.jpa;

import com.example.fanplatform.membership.domain.billing.BillingKeyEnrollment;
import com.example.fanplatform.membership.domain.membership.MembershipTier;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link BillingKeyEnrollment}.
 */
public interface BillingKeyEnrollmentJpaRepository extends JpaRepository<BillingKeyEnrollment, String> {

    Optional<BillingKeyEnrollment> findByAccountIdAndTenantIdAndTierAndActiveTrue(
            String accountId, String tenantId, MembershipTier tier);

    /** Cross-tenant active scan for the auto-renew scheduler; oldest first, capped. */
    List<BillingKeyEnrollment> findByActiveTrueOrderByCreatedAtAsc(Pageable pageable);

    /**
     * Direct bulk deactivation (executes immediately as a SQL UPDATE, before the
     * subsequent insert flush) — see {@code BillingKeyEnrollmentRepository#deactivateActive}.
     */
    @Modifying
    @Query("UPDATE BillingKeyEnrollment e SET e.active = false "
            + "WHERE e.accountId = :accountId AND e.tenantId = :tenantId AND e.tier = :tier AND e.active = true")
    int deactivateActive(@Param("accountId") String accountId,
                         @Param("tenantId") String tenantId,
                         @Param("tier") MembershipTier tier);
}
