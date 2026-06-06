package com.example.account.application.service;

import com.example.account.application.exception.EmailAlreadyVerifiedException;
import com.example.account.application.exception.RateLimitedException;
import com.example.account.application.exception.AccountNotFoundException;
import com.example.account.application.port.EmailVerificationNotifier;
import com.example.account.domain.account.Account;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.repository.EmailVerificationTokenStore;
import com.example.account.domain.tenant.TenantId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.UUID;

/**
 * Use case for {@code POST /api/accounts/signup/resend-verification-email}
 * (TASK-BE-114).
 *
 * <p>Behaviour contract:
 * <ol>
 *   <li>Load the account. Missing → 404 {@code ACCOUNT_NOT_FOUND}.</li>
 *   <li>If {@code emailVerifiedAt} is already set, reject with
 *       {@link EmailAlreadyVerifiedException} (409). No token is issued.</li>
 *   <li>Acquire a 5-minute rate-limit slot via {@link EmailVerificationTokenStore#tryAcquireResendSlot}.
 *       Marker already exists → {@link RateLimitedException} (429). Redis
 *       failure is swallowed inside the store (fail-open) so service stays
 *       available during incidents.</li>
 *   <li>Mint a UUID v4 token, save {@code token → accountId} with a 24-hour
 *       TTL, then trigger the email send. Email send failures are swallowed
 *       at WARN (without the token payload) — the user can request another
 *       resend after the rate-limit window.</li>
 * </ol>
 *
 * <p>Marked {@code @Transactional(readOnly = true)} purely for the account
 * lookup — Redis writes and the email send are non-transactional by nature.</p>
 *
 * <p>R4: the token is never logged. The account's plaintext email is passed
 * to the notifier, which is responsible for masking it before any log
 * emission.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SendVerificationEmailUseCase {

    private static final Duration TOKEN_TTL = Duration.ofHours(24);
    private static final Duration RESEND_RATE_LIMIT_TTL = Duration.ofSeconds(300);

    private final AccountRepository accountRepository;
    private final EmailVerificationTokenStore tokenStore;
    private final EmailVerificationNotifier notifier;

    @Transactional(readOnly = true)
    public void execute(String accountId) {
        // 1) Account must exist.
        // TASK-BE-228: tenant context is fixed to FAN_PLATFORM until TASK-BE-229.
        Account account = accountRepository.findById(TenantId.FAN_PLATFORM, accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        // 2) Idempotent guard: don't issue a token that cannot be consumed.
        if (account.getEmailVerifiedAt() != null) {
            throw new EmailAlreadyVerifiedException();
        }

        // 3) Rate limit (best-effort, fail-open inside the store on Redis
        //    outage — see RedisEmailVerificationTokenStore).
        boolean acquired = tokenStore.tryAcquireResendSlot(accountId, RESEND_RATE_LIMIT_TTL);
        if (!acquired) {
            throw new RateLimitedException("Resend rate limit exceeded — try again later");
        }

        // 4) Mint and persist the token, then send the email. Send failures
        //    do not roll back the token write: the user can wait for the
        //    rate-limit window to expire and try again.
        String token = UUID.randomUUID().toString();
        tokenStore.save(token, accountId, TOKEN_TTL);

        try {
            notifier.sendVerificationEmail(account.getEmail(), token);
        } catch (Exception e) {
            // Best-effort. Do NOT log the token (R4).
            log.warn("Failed to send verification email for accountId={}: {}",
                    accountId, e.getMessage());
        }
    }
}
