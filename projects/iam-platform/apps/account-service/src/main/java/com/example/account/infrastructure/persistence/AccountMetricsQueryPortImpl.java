package com.example.account.infrastructure.persistence;

import com.example.account.application.port.AccountMetricsQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/** JPA adapter for {@link AccountMetricsQueryPort} (TASK-MONO-811). */
@Component
@RequiredArgsConstructor
public class AccountMetricsQueryPortImpl implements AccountMetricsQueryPort {

    private final AccountJpaRepository accountJpaRepository;
    private final ConsumerSiteMembershipJpaRepository membershipJpaRepository;

    @Override
    public List<Instant> accountsCreatedIn(String tenantId, Instant from, Instant to) {
        return accountJpaRepository.findCreatedAtInWindow(tenantId, from, to);
    }

    @Override
    public List<Instant> accountsCreatedInAllTenants(Instant from, Instant to) {
        return accountJpaRepository.findCreatedAtInWindowAllTenants(from, to);
    }

    @Override
    public List<Instant> siteMembershipsConsentedIn(String siteTenantId, Instant from, Instant to) {
        return membershipJpaRepository.findConsentedAtInWindow(siteTenantId, from, to);
    }
}
