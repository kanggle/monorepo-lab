package com.example.fanplatform.membership.infrastructure.jpa;

import com.example.fanplatform.membership.domain.membership.Membership;
import com.example.fanplatform.membership.domain.membership.MembershipRepository;
import com.example.fanplatform.membership.domain.membership.status.MembershipStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * JPA adapter satisfying the {@link MembershipRepository} domain port.
 */
@Component
@RequiredArgsConstructor
public class MembershipRepositoryImpl implements MembershipRepository {

    private final MembershipJpaRepository jpa;

    @Override
    public Membership save(Membership membership) {
        return jpa.save(membership);
    }

    @Override
    public Optional<Membership> findByIdScoped(String id, String accountId, String tenantId) {
        return jpa.findByIdAndAccountIdAndTenantId(id, accountId, tenantId);
    }

    @Override
    public List<Membership> findByAccount(String accountId, String tenantId) {
        return jpa.findByAccountIdAndTenantIdOrderByValidToDesc(accountId, tenantId);
    }

    @Override
    public List<Membership> findActiveByAccount(String accountId, String tenantId) {
        return jpa.findByAccountIdAndTenantIdAndStatus(accountId, tenantId, MembershipStatus.ACTIVE);
    }
}
