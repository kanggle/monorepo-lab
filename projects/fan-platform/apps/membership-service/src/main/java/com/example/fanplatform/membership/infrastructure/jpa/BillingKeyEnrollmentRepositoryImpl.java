package com.example.fanplatform.membership.infrastructure.jpa;

import com.example.fanplatform.membership.domain.billing.BillingKeyEnrollment;
import com.example.fanplatform.membership.domain.billing.BillingKeyEnrollmentRepository;
import com.example.fanplatform.membership.domain.membership.MembershipTier;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * JPA adapter satisfying the {@link BillingKeyEnrollmentRepository} domain port.
 */
@Component
@RequiredArgsConstructor
public class BillingKeyEnrollmentRepositoryImpl implements BillingKeyEnrollmentRepository {

    private final BillingKeyEnrollmentJpaRepository jpa;

    @Override
    public BillingKeyEnrollment save(BillingKeyEnrollment enrollment) {
        return jpa.save(enrollment);
    }

    @Override
    public Optional<BillingKeyEnrollment> findActiveByAccountAndTier(
            String accountId, String tenantId, MembershipTier tier) {
        return jpa.findByAccountIdAndTenantIdAndTierAndActiveTrue(accountId, tenantId, tier);
    }

    @Override
    public List<BillingKeyEnrollment> findAllActive(int limit) {
        return jpa.findByActiveTrueOrderByCreatedAtAsc(PageRequest.of(0, limit));
    }

    @Override
    public int deactivateActive(String accountId, String tenantId, MembershipTier tier) {
        return jpa.deactivateActive(accountId, tenantId, tier);
    }
}
