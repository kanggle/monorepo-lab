package com.example.fanplatform.membership.infrastructure.jpa;

import com.example.fanplatform.membership.domain.membership.Membership;
import com.example.fanplatform.membership.domain.membership.status.MembershipStatus;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
