package com.example.account.application.service;

import com.example.account.application.exception.EmailAlreadyVerifiedException;
import com.example.account.application.exception.EmailVerificationTokenInvalidException;
import com.example.account.application.result.VerifyEmailResult;
import com.example.account.domain.account.Account;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.repository.EmailVerificationTokenStore;
import com.example.account.domain.tenant.TenantId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Use case for {@code POST /api/accounts/signup/verify-email} (TASK-BE-114).
 *
 * <p>Behaviour contract:
 * <ol>
 *   <li>Resolve {@code token → accountId} from the Redis token store. Missing
 *       / expired / already-used tokens all surface as
 *       {@link EmailVerificationTokenInvalidException} so the API does not
 *       leak token state.</li>
 *   <li>Load the account. If it has been deleted between resend and verify,
 *       treat it as an invalid token (uniform response).</li>
 *   <li>Call {@link Account#verifyEmail(Instant)} and persist. If the account
 *       is already verified the domain throws {@link IllegalStateException},
 *       which we translate to {@link EmailAlreadyVerifiedException} → 409.</li>
 *   <li>Delete the token from Redis <strong>after</strong> the DB write has
 *       succeeded. If the delete itself fails (transient Redis hiccup) we log
 *       a warning and swallow — the verifyEmail commit has already happened
 *       and rethrowing would mislead the user. The TTL guarantees eventual
 *       cleanup.</li>
 * </ol>
 *
 * <p>Account status is unchanged — verification is non-blocking by design
 * (specs/features/signup.md).</p>
 *
 * <p>R4 compliance: the token is never logged. Only {@code accountId} (a UUID,
 * not PII) appears in log lines.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerifyEmailUseCase {

    private final EmailVerificationTokenStore tokenStore;
    private final AccountRepository accountRepository;

    @Transactional
    public VerifyEmailResult execute(String token) {
        // 1) Resolve token. Missing / expired → uniform 400.
        String accountId = tokenStore.findAccountId(token)
                .orElseThrow(EmailVerificationTokenInvalidException::new);

        // 2) Load account. Vanished accounts surface as the same uniform 400.
        // TASK-BE-228: tenant context is fixed to FAN_PLATFORM until TASK-BE-229.
        Account account = accountRepository.findById(TenantId.FAN_PLATFORM, accountId)
                .orElseThrow(EmailVerificationTokenInvalidException::new);

        // 3) Domain rule: emailVerifiedAt is a write-once field. Already-verified
        //    accounts surface as 409 to the caller.
        Instant now = Instant.now();
        try {
            account.verifyEmail(now);
        } catch (IllegalStateException e) {
            throw new EmailAlreadyVerifiedException();
        }

        // 4) Persist before the token delete so a transient Redis failure on
        //    delete does not leave us with an unverified row + missing token.
        accountRepository.save(account);

        // 5) Single-use enforcement: delete the token last. Best-effort.
        try {
            tokenStore.delete(token);
        } catch (Exception e) {
            // The verify is already committed — swallowing is correct. The
            // token's TTL (24h) will clean up eventually.
            log.warn("Failed to delete email verification token after verify "
                    + "(accountId={}): {}", accountId, e.getMessage());
        }

        log.info("Email verified for accountId={}", accountId);
        return new VerifyEmailResult(accountId, account.getEmailVerifiedAt());
    }

}
