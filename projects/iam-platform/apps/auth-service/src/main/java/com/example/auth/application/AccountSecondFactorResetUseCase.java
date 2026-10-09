package com.example.auth.application;

import com.example.auth.application.port.AccountServicePort;
import com.example.auth.domain.mfa.AccountTotp;
import com.example.auth.domain.repository.AccountTotpRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * TASK-MONO-771 S6 (ticket Edge Case 2, owner decision OD-6) — the auth half of the administrator reset of an
 * account's second factor (admin-to-auth.md § POST /internal/auth/accounts/{accountId}/second-factor/reset).
 *
 * <p>admin-service has already decided WHO may do this (platform roles, platform scope), required a reason and
 * written the IN_PROGRESS audit row; this class only deletes the {@code account_totp} row. It does not re-decide
 * authorization — the {@code /internal/auth/**} {@code internal.invoke} workload gate is what makes sure only a
 * system caller reaches it.
 *
 * <p><b>Order</b>: a row (confirmed or pending) → delete it, regardless of whether account-service still has the
 * account (a secret left behind a deleted account is better gone). No row → nothing was deleted; only then is
 * account-service asked, so the operator can tell «no such account» from «nothing enrolled». A failed read there
 * propagates ({@code AccountServiceUnavailableException} → 503) instead of guessing one of the two.
 *
 * <p>After a reset the account is exactly «not enrolled»: {@link AccountSecondFactorService#hasConfirmedEnrollment}
 * is {@code false} (the question the authorize second-factor gate asks), so a step-up / policy entry is routed to
 * {@code /mfa/setup}, where the OD-4 enrolment preconditions (verified e-mail, notice mail) apply again. Sessions and
 * tokens are not touched (contract).
 */
@Slf4j
@Service
public class AccountSecondFactorResetUseCase {

    private final AccountTotpRepository repository;
    private final AccountServicePort accountServicePort;
    private final Clock clock;

    public AccountSecondFactorResetUseCase(AccountTotpRepository repository, AccountServicePort accountServicePort,
                                           Clock clock) {
        this.repository = repository;
        this.accountServicePort = accountServicePort;
        this.clock = clock;
    }

    /**
     * @param accountId  the account whose enrolment is deleted
     * @param operatorId the admin operator (log correlation only — the audit row lives in admin-service)
     * @return what happened; never {@code null}
     * @throws com.example.auth.application.exception.AccountServiceUnavailableException when there was no row and
     *         the account's existence could not be read
     */
    @Transactional
    public Result reset(String accountId, String operatorId) {
        Optional<AccountTotp> row = repository.findByAccountId(accountId);
        if (row.isPresent()) {
            boolean wasConfirmed = row.get().isConfirmed();
            repository.deleteByAccountId(accountId);
            Instant resetAt = clock.instant();
            log.info("Account second factor reset by administrator: accountId={} operatorId={} wasConfirmed={}",
                    accountId, operatorId, wasConfirmed);
            return new Result(Outcome.RESET, accountId, resetAt, wasConfirmed);
        }
        Outcome outcome = accountServicePort.getAccountStatusAndTenant(accountId).isPresent()
                ? Outcome.NOT_ENROLLED
                : Outcome.ACCOUNT_NOT_FOUND;
        log.info("Account second factor reset found nothing to delete: accountId={} operatorId={} outcome={}",
                accountId, operatorId, outcome);
        return new Result(outcome, accountId, null, false);
    }

    /** What {@link #reset} found. */
    public enum Outcome {
        /** A row was deleted. */
        RESET,
        /** No row; the account exists — nothing to reset. */
        NOT_ENROLLED,
        /** No row; account-service has no such account. */
        ACCOUNT_NOT_FOUND
    }

    /**
     * @param resetAt      when the row was deleted ({@code null} unless {@link Outcome#RESET})
     * @param wasConfirmed whether the deleted row was a confirmed enrolment ({@code false} = a pending one only)
     */
    public record Result(Outcome outcome, String accountId, Instant resetAt, boolean wasConfirmed) {}
}
