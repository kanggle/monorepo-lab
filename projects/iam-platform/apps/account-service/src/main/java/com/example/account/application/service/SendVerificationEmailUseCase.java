package com.example.account.application.service;

import com.example.account.application.exception.EmailAlreadyVerifiedException;
import com.example.account.application.exception.EmailDeliveryException;
import com.example.account.application.exception.VerificationEmailSendFailedException;
import com.example.account.application.exception.RateLimitedException;
import com.example.account.application.exception.AccountNotFoundException;
import com.example.account.application.port.ConsumerPoolFlag;
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
 *       TTL, then trigger the email send.</li>
 *   <li>TASK-MONO-770: a send failure is no longer swallowed — the token is discarded, the rate-limit slot
 *       released, and {@link VerificationEmailSendFailedException} carries the failure's kind (503 transient /
 *       422 permanent).</li>
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
    /** TASK-BE-616 — § 5: the site tenant finds that site's ACTIVE pool members too ({@link SiteAccountLookup}). */
    private final ConsumerPoolFlag consumerPoolFlag;

    /**
     * NET-ZERO overload — a header-less caller stays pinned to {@link TenantId#FAN_PLATFORM},
     * byte-identical to the pre-BE-507 behaviour.
     */
    @Transactional(readOnly = true)
    public void execute(String accountId) {
        execute(accountId, TenantId.FAN_PLATFORM);
    }

    /**
     * TASK-BE-507 — tenant-aware resend. The tenant is minted into the token
     * ({@link EmailVerificationTokenStore#save}) so the verify path, which is
     * token-authenticated and sees no {@code X-Tenant-Id}, can scope its own lookup.
     */
    @Transactional(readOnly = true)
    public void execute(String accountId, TenantId tenantId) {
        // 1) Account must exist in the caller's tenant.
        Account account = SiteAccountLookup.find(accountRepository, consumerPoolFlag, tenantId, accountId)
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

        // 4) Mint and persist the token, then send the email.
        String token = UUID.randomUUID().toString();
        // TASK-BE-616: the token carries the account's OWN tenant (consumer-pool for a pool member found
        // through a site), so the token-authenticated verify path keeps its exact (tenant, id) lookup.
        // For a site account the two are the same value — byte-identical.
        tokenStore.save(token, account.getTenantId().value(), accountId, TOKEN_TTL);

        // 5) TASK-MONO-770 — a send failure is an ANSWER, no longer a swallowed WARN. Before, the endpoint said
        //    204 «sent» for a mail that never left; with verification now the condition for a company role
        //    (ADR-MONO-080 D3) that lie reads as «I was not given the role». So: discard the token nobody
        //    received, give the rate-limit slot back (the person must be able to retry at once), and say which
        //    kind of failure it was.
        try {
            notifier.sendVerificationEmail(account.getEmail(), token);
        } catch (EmailDeliveryException e) {
            undoIssuance(token, accountId);
            // R4: neither the token nor the address — and not e.getMessage() either (adapters build it from a
            // fixed string, but the log line must not depend on that discipline holding).
            log.warn("Verification email not sent for accountId={}: kind={}", accountId, e.getKind());
            throw new VerificationEmailSendFailedException(e.getKind());
        } catch (RuntimeException e) {
            // A notifier that broke the port contract (threw something unclassified). Cannot judge → TRANSIENT:
            // calling an unknown failure permanent tells the person to give up on something that may work.
            undoIssuance(token, accountId);
            log.warn("Verification email not sent for accountId={}: unclassified failure type={}",
                    accountId, e.getClass().getName());
            throw new VerificationEmailSendFailedException(EmailDeliveryException.Kind.TRANSIENT);
        }
    }

    private void undoIssuance(String token, String accountId) {
        try {
            tokenStore.delete(token);
        } catch (RuntimeException e) {
            // The token was never delivered; if it cannot be deleted it simply expires with its TTL.
            log.warn("Undelivered verification token could not be discarded for accountId={} (expires with TTL)",
                    accountId);
        }
        tokenStore.releaseResendSlot(accountId);
    }
}
