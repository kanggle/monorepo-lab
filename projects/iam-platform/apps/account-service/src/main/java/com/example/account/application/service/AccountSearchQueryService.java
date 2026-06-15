package com.example.account.application.service;

import com.example.account.application.port.AccountQueryPort;
import com.example.account.application.result.AccountDetailResult;
import com.example.account.application.result.AccountSearchResult;
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
     * TASK-BE-357: tenant-scoped search/list. {@code tenantId} is the concrete tenant
     * admin-service already resolved + effective-scope-gated. Fail-closed: a blank
     * {@code tenantId} throws {@code IllegalArgumentException} (→ 400 VALIDATION_ERROR)
     * rather than degrading to an implicit cross-tenant scan.
     */
    @Transactional(readOnly = true)
    public AccountSearchResult search(String tenantId, String email, int page, int size) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be ≤ " + MAX_PAGE_SIZE);
        }

        if (email == null || email.isBlank()) {
            return accountQueryPort.findAll(tenantId, page, size);
        }

        List<AccountSearchResult.Item> items = accountQueryPort.findByEmail(tenantId, email.trim());
        return new AccountSearchResult(items, items.size(), 0, size, items.isEmpty() ? 0 : 1);
    }

    @Transactional(readOnly = true)
    public Optional<AccountDetailResult> detail(String accountId) {
        return accountQueryPort.findDetailById(accountId);
    }
}
