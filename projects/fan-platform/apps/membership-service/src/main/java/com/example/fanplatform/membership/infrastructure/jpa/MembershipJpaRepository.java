package com.example.fanplatform.membership.infrastructure.jpa;

import com.example.fanplatform.membership.domain.membership.Membership;
import com.example.fanplatform.membership.domain.membership.status.MembershipStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Membership}. Every derived method is
 * tenant-scoped (multi-tenant.md M2).
 */
public interface MembershipJpaRepository extends JpaRepository<Membership, String> {

    Optional<Membership> findByIdAndAccountIdAndTenantId(String id, String accountId, String tenantId);

    List<Membership> findByAccountIdAndTenantIdOrderByValidToDesc(String accountId, String tenantId);

    List<Membership> findByAccountIdAndTenantIdAndStatus(
            String accountId, String tenantId, MembershipStatus status);

    /**
     * Expiry-sweeper candidate batch (cross-tenant): status = given, valid_to <
     * given instant, expiry_notified_at IS NULL, oldest window first. The
     * {@link Pageable} caps the batch size. Matches the partial index
     * {@code idx_memberships_expiry_sweep}.
     */
    List<Membership> findByStatusAndValidToLessThanAndExpiryNotifiedAtIsNullOrderByValidToAsc(
            MembershipStatus status, Instant validTo, Pageable pageable);
}
