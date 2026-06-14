package com.example.account.application.service;

import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.tenant.TenantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * TASK-BE-372 (ADR-MONO-034 U6 step 3b): Read-only resolution of an account's
 * central {@code identity_id} (the registry introduced in step 3a, V0023). This
 * is the foundation the operator-link surface (3c) and unified provisioning (3d)
 * build on — given an account_id (e.g. an operator's {@code oidc_subject}),
 * resolve the central identity it belongs to.
 *
 * <p>Returns {@link Optional#empty()} when the account does not exist in the
 * tenant OR has no identity yet (enumeration-safe — no 404; the caller fail-softs).
 * Net-zero: no audit row, no outbox event, no mutation.
 */
@Service
@RequiredArgsConstructor
public class GetAccountIdentityUseCase {

    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public Optional<String> execute(String tenantId, String accountId) {
        TenantId tid = new TenantId(tenantId);
        return accountRepository.findIdentityId(tid, accountId);
    }
}
