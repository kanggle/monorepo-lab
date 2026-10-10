package com.example.account.application.service;

import com.example.account.application.exception.AccountEmailMismatchException;
import com.example.account.application.exception.AccountNotFoundException;
import com.example.account.application.exception.EmailNotVerifiedException;
import com.example.account.application.result.VerifiedEmailMatchResult;
import com.example.account.domain.account.Account;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.tenant.TenantId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * TASK-MONO-772 S2 (ADR-MONO-080 D3 · D6, AC-0 F3 · implementer decision D-3 step 4) —
 * {@code POST /internal/accounts/{accountId}/verified-email:match} (admin-to-account.md): «is this account a pool
 * account that verified THIS email?». The operator-invitation acceptance in admin-service asks it before it
 * attaches an operator facet; admin-service holds no copy of the predicate.
 *
 * <p><b>Order — the first failure answers, nothing is written</b> (the same order and the same predicates as the
 * seller-member site-role grant, {@link ConsumerSiteRoleWriteUseCase}, so a change to the rule moves both):
 * <ol>
 *   <li>the account is found under {@link TenantId#CONSUMER_POOL} and is {@code ACTIVE} — anything else (no such
 *       id · a site or B2B account · locked/dormant/deleted) is ONE answer, {@link AccountNotFoundException}
 *       (S1-3: telling them apart would need a tenant-less lookup, and the caller only needs «not attachable»);</li>
 *   <li>{@code expectedEmail} is the account's own email ({@link ConsumerSiteRoleWriteUseCase#sameEmail} — trim,
 *       case-insensitive) → else {@link AccountEmailMismatchException};</li>
 *   <li>🔴 {@link VerifiedEmailRequirement#require} — TASK-MONO-770's shared predicate, called as is →
 *       {@link EmailNotVerifiedException} ({@code 403 EMAIL_NOT_VERIFIED}).</li>
 * </ol>
 * The wrong address answers before the unverified one (the more specific answer — site-role rules 4 → 4b).
 *
 * <p>Read-only: no audit row, no event, no write. The verification is the evidence at the moment of attaching,
 * not a condition for keeping (ADR-MONO-080 D5) — this never revokes anything. R4: the expected email is never
 * logged; log lines name the account id and the outcome only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerifiedEmailMatchUseCase {

    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public VerifiedEmailMatchResult match(String accountId, String expectedEmail) {
        Account account = accountRepository.findById(TenantId.CONSUMER_POOL, accountId)
                .filter(a -> a.getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> {
                    log.info("verified-email match refused: not an ACTIVE pool account (account={})", accountId);
                    return new AccountNotFoundException(accountId);
                });

        if (!ConsumerSiteRoleWriteUseCase.sameEmail(expectedEmail, account.getEmail())) {
            log.info("verified-email match refused: email mismatch (account={})", accountId);
            throw new AccountEmailMismatchException(
                    "The account's email is not the expected one; nothing can be attached");
        }
        try {
            VerifiedEmailRequirement.require(account);
        } catch (EmailNotVerifiedException refused) {
            log.info("verified-email match refused: email not verified (account={})", accountId);
            throw refused;
        }
        return new VerifiedEmailMatchResult(account.getId(), account.getEmailVerifiedAt());
    }
}
