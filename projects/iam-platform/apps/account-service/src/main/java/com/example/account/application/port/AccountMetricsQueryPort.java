package com.example.account.application.port;

import java.time.Instant;
import java.util.List;

/**
 * TASK-MONO-811 — the timestamps behind the IAM overview {@code newAccount} metric
 * ({@code admin-to-account.md} § GET /internal/account-metrics/daily). Every method returns only
 * instants (no id, email or status) for rows inside the half-open window {@code [from, to)}.
 *
 * <p>Implemented by the infrastructure layer ({@code AccountMetricsQueryPortImpl}).
 */
public interface AccountMetricsQueryPort {

    /** {@code accounts.created_at} of one tenant's rows (every status). */
    List<Instant> accountsCreatedIn(String tenantId, Instant from, Instant to);

    /** {@code accounts.created_at} across every tenant. */
    List<Instant> accountsCreatedInAllTenants(Instant from, Instant to);

    /** {@code consumer_site_memberships.consented_at} of one consumer site's rows (every status). */
    List<Instant> siteMembershipsConsentedIn(String siteTenantId, Instant from, Instant to);
}
