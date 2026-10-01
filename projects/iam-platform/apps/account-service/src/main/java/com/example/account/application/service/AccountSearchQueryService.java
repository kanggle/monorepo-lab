package com.example.account.application.service;

import com.example.account.application.port.AccountQueryPort;
import com.example.account.application.result.AccountDetailResult;
import com.example.account.application.result.AccountSearchResult;
import com.example.account.domain.status.AccountStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AccountSearchQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AccountQueryPort accountQueryPort;
    /**
     * TASK-BE-614 (multi-tenancy.md § 소비자 계정 풀 § 5): this is the search behind the console's
     * customer-account operations AND admin-service {@code CreateOperatorUseCase}'s "a signed-up
     * account exists in the target tenant" check. With the pool on, a site search includes that
     * site's pool members. Flag off: the old tenant-only queries, unchanged.
     */
    private final ConsumerAccountPool consumerAccountPool;

    /**
     * TASK-BE-357: tenant-scoped search/list. {@code tenantId} is the concrete tenant
     * admin-service already resolved + effective-scope-gated. Fail-closed: a blank
     * {@code tenantId} throws {@code IllegalArgumentException} (→ 400 VALIDATION_ERROR)
     * rather than degrading to an implicit cross-tenant scan.
     */
    @Transactional(readOnly = true)
    public AccountSearchResult search(String tenantId, String email, String status, int page, int size) {
        return search(tenantId, email, status, page, size, false);
    }

    /**
     * TASK-BE-615 AC-8 (owner decision, 2026-10-01): {@code excludePoolMembers = true} answers with the
     * site's OWN accounts only — the pre-pool query — even when the pool is on. admin-service
     * {@code CreateOperatorUseCase}'s "a signed-up account exists in the target tenant" check asks
     * this way: an operator's account rules change only with ADR-MONO-080 ({@code TASK-MONO-746}),
     * and a pool member passing that check would get a NEW site-tenant identity
     * ({@code resolveOrCreateIdentity}) beside its pool one. The console account-operations listing
     * keeps {@code false} — § 5 says site lookups include that site's pool members.
     */
    @Transactional(readOnly = true)
    public AccountSearchResult search(String tenantId, String email, String status, int page, int size,
                                      boolean excludePoolMembers) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be ≤ " + MAX_PAGE_SIZE);
        }

        boolean includePoolMembers = !excludePoolMembers && consumerAccountPool.lookupsIncludePoolMembers();

        if (email == null || email.isBlank()) {
            // TASK-BE-475: optional status filter (list branch only). Fail-closed parse —
            // a bad value → 400 VALIDATION_ERROR (defense in depth; admin-service already
            // validated the allow-set before forwarding). null/blank → no filter.
            AccountStatus statusFilter = parseStatus(status);
            return includePoolMembers
                    ? accountQueryPort.findAllIncludingPoolMembers(tenantId, statusFilter, page, size)
                    : accountQueryPort.findAll(tenantId, statusFilter, page, size);
        }

        List<AccountSearchResult.Item> items = includePoolMembers
                ? accountQueryPort.findByEmailIncludingPoolMembers(tenantId, email.trim())
                : accountQueryPort.findByEmail(tenantId, email.trim());
        return new AccountSearchResult(items, items.size(), 0, size, items.isEmpty() ? 0 : 1);
    }

    /** TASK-BE-475: null/blank → no filter; a non-enum value → IllegalArgumentException (400). */
    private static AccountStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return AccountStatus.valueOf(status.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid account status filter: " + status);
        }
    }

    @Transactional(readOnly = true)
    public Optional<AccountDetailResult> detail(String accountId) {
        return accountQueryPort.findDetailById(accountId);
    }
}
