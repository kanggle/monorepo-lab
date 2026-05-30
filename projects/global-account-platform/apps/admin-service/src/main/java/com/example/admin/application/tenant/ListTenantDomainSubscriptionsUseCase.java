package com.example.admin.application.tenant;

import com.example.admin.application.port.TenantDomainSubscriptionPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * TASK-BE-322 (ADR-MONO-019 D4): read-through proxy to account-service for the
 * ACTIVE tenant↔domain subscriptions that drive the console catalog binding.
 * No audit row for read paths.
 */
@Service
@RequiredArgsConstructor
public class ListTenantDomainSubscriptionsUseCase {

    private final TenantDomainSubscriptionPort subscriptionPort;

    public List<TenantDomainSubscriptionSummary> execute() {
        return subscriptionPort.listActiveSubscriptions();
    }
}
