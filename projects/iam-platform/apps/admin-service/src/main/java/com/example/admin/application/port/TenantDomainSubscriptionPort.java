package com.example.admin.application.port;

import com.example.admin.application.tenant.TenantDomainSubscriptionSummary;

import java.util.List;

/**
 * TASK-BE-322 (ADR-MONO-019 D4): port for reading tenant↔domain subscriptions
 * from account-service (the D2 entitlement authority).
 *
 * <p>Implemented by {@code AccountServiceTenantClient} (infrastructure/client).
 * admin-service is NOT the source of truth for subscription data — all reads
 * flow through account-service's internal API. This port isolates the console
 * catalog projection ({@code ConsoleRegistryUseCase}) from HTTP client details.
 */
public interface TenantDomainSubscriptionPort {

    /**
     * Returns all ACTIVE tenant↔domain subscriptions.
     *
     * @throws com.example.admin.application.exception.DownstreamFailureException on 5xx/CB open
     */
    List<TenantDomainSubscriptionSummary> listActiveSubscriptions();
}
