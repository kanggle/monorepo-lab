package com.example.admin.application.port;

import com.example.admin.application.tenant.SubscriptionMutationSummary;
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

    /**
     * TASK-BE-343 (ADR-MONO-023 D3): subscribe (create, ACTIVE) — delegates to
     * account-service {@code POST /internal/tenant-domain-subscriptions}.
     *
     * @throws com.example.admin.application.exception.TenantNotFoundException             unknown tenant (404)
     * @throws com.example.admin.application.exception.SubscriptionAlreadyExistsException  duplicate (409)
     * @throws com.example.admin.application.exception.DownstreamFailureException          5xx/CB-open
     */
    SubscriptionMutationSummary subscribe(String tenantId, String domainKey, String reason, String actorId);

    /**
     * TASK-BE-343 (ADR-MONO-023 D1/D3): transition (suspend/resume/cancel) —
     * delegates to account-service {@code PATCH /internal/tenant-domain-subscriptions/{t}/{d}}.
     *
     * @throws com.example.admin.application.exception.SubscriptionNotFoundException           no such subscription (404)
     * @throws com.example.admin.application.exception.SubscriptionTransitionInvalidException  illegal transition (409)
     * @throws com.example.admin.application.exception.DownstreamFailureException              5xx/CB-open
     */
    SubscriptionMutationSummary changeStatus(String tenantId, String domainKey,
                                             String targetStatus, String reason, String actorId);
}
