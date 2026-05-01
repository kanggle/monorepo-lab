package com.example.security.domain.repository;

import com.example.security.domain.history.LoginHistoryEntry;

import java.util.Optional;

public interface LoginHistoryRepository {

    void save(LoginHistoryEntry entry);

    boolean existsByEventId(String eventId);

    /**
     * Find the most recent successful login for the given (tenant, account),
     * excluding the current event. Used by ImpossibleTravelRule.
     *
     * <p>TASK-BE-248: tenantId is required — the {@code login_history} index
     * leads with {@code tenant_id} so per-tenant lookups stay index-scan-only.
     * Cross-tenant analysis requires an explicit, separate API.
     */
    Optional<LoginHistoryEntry> findLatestSuccessByAccountId(String tenantId, String accountId);
}
