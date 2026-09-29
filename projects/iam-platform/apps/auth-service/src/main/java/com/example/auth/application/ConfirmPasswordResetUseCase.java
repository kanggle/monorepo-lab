package com.example.auth.application;

import com.example.auth.application.command.ConfirmPasswordResetCommand;
import com.example.auth.application.exception.PasswordResetTokenInvalidException;
import com.example.auth.application.port.AccountServicePort;
import com.example.auth.application.port.OAuthAuthorizationRevocationPort;
import com.example.auth.application.port.TokenGeneratorPort;
import com.example.auth.domain.credentials.Credential;
import com.example.auth.domain.credentials.CredentialHash;
import com.example.auth.domain.credentials.PasswordPolicy;
import com.example.auth.domain.repository.AccessTokenInvalidationStore;
import com.example.auth.domain.repository.BulkInvalidationStore;
import com.example.auth.domain.repository.CredentialRepository;
import com.example.auth.domain.repository.PasswordResetTokenStore;
import com.example.auth.domain.repository.RefreshTokenRepository;
import com.example.security.password.PasswordHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;

/**
 * Use case for {@code POST /api/auth/password-reset/confirm} (TASK-BE-109).
 *
 * <p>Consumes a previously-issued password reset token, validates the new
 * password against {@link PasswordPolicy}, persists the new credential hash,
 * revokes every refresh token + sets a bulk-invalidation marker for the
 * account, writes the access-token invalidation marker (TASK-BE-146), and
 * finally deletes the token from Redis to enforce single-use semantics.</p>
 *
 * <p><strong>Ordering matters.</strong> The token is deleted <em>last</em>:
 * if persisting the new hash or the session revoke fails, the transaction
 * rolls back and the token must remain valid in Redis so the user can retry.
 * See task spec "Failure Scenarios".</p>
 *
 * <p>R4 (rules/traits/regulated.md): the plaintext password and the reset
 * token are never logged. The only INFO log line identifies the {@code accountId}
 * and the count of revoked tokens.</p>
 *
 * <p><strong>TASK-BE-607.</strong> Revoking the {@code refresh_tokens} mirror rows by account
 * UUID does not, by itself, end every SAS browser session: a session whose mirror row predates
 * TASK-BE-603 is still keyed by the login email (grace window up to 30 days), so
 * {@code revokeAllByAccountId} never matches it and the SAS authorization backing it stays
 * active. This use case therefore also closes the account's SAS authorizations directly via
 * {@link OAuthAuthorizationRevocationPort} — the same shape {@code ForceLogoutUseCase} uses
 * (TASK-BE-601) — so the reset ends a session regardless of which shape its mirror row has.</p>
 *
 * <p>Failure modes:
 * <ul>
 *   <li>token unknown / expired → {@link PasswordResetTokenInvalidException}</li>
 *   <li>credential row missing for the resolved account →
 *       {@link PasswordResetTokenInvalidException} (uniform response per spec
 *       Edge Case "계정이 삭제된 경우")</li>
 *   <li>{@code newPassword} fails {@link PasswordPolicy} →
 *       {@code PasswordPolicyViolationException} (mapped to 400
 *       {@code PASSWORD_POLICY_VIOLATION})</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfirmPasswordResetUseCase {

    private final PasswordResetTokenStore passwordResetTokenStore;
    private final CredentialRepository credentialRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final BulkInvalidationStore bulkInvalidationStore;
    private final AccessTokenInvalidationStore accessTokenInvalidationStore;
    private final TokenGeneratorPort tokenGeneratorPort;
    private final PasswordHasher passwordHasher;
    private final OAuthAuthorizationRevocationPort oAuthAuthorizationRevocationPort;
    private final AccountServicePort accountServicePort;

    @Transactional
    public void execute(ConfirmPasswordResetCommand command) {
        // 1) Resolve the token. Missing/expired/already-used all surface as the
        //    same exception so the API does not leak token state.
        String accountId = passwordResetTokenStore.findAccountId(command.token())
                .orElseThrow(PasswordResetTokenInvalidException::new);

        // 2) Locate the credential row. If the account was deleted between
        //    request and confirm, treat it as an invalid token (uniform response).
        Credential credential = credentialRepository.findByAccountId(accountId)
                .orElseThrow(PasswordResetTokenInvalidException::new);

        // 3) Validate against the password policy. PolicyViolationException
        //    intentionally never includes the password value (R4).
        PasswordPolicy.validate(command.newPassword(), credential.getEmail());

        // Capture revocation timestamp once so the credential's changedAt and
        // both invalidation markers reference the same instant.
        Instant revokedAt = Instant.now();

        // 4) Persist the new hash. Argon2id is expensive — only run after the
        //    cheaper checks above have passed.
        String newHash = passwordHasher.hash(command.newPassword());
        Credential updated = credential.changePassword(
                CredentialHash.argon2id(newHash), revokedAt);
        credentialRepository.save(updated);

        // 5) Revoke every active refresh token for the account and set a
        //    bulk-invalidation marker so any in-flight refresh attempts with
        //    a token issued before this instant fail closed. Both calls are
        //    idempotent and independently safe (see ForceLogoutUseCase).
        int legacyRevoked = refreshTokenRepository.revokeAllByAccountId(accountId);
        // TASK-BE-607: also close the account's SAS authorizations. The mirror-row revoke above
        // is keyed by the account UUID and never reaches a session whose mirror row predates
        // TASK-BE-603 (email-keyed); this port reaches every session's authorization regardless
        // (same shape as ForceLogoutUseCase, TASK-BE-601).
        int sasRevoked = oAuthAuthorizationRevocationPort.revokeActiveRefreshTokens(accountId);
        bulkInvalidationStore.invalidateAll(
                accountId, tokenGeneratorPort.refreshTokenTtlSeconds());

        // 6) TASK-BE-146: write access-token invalidation marker so the gateway
        //    rejects any access token issued at or before {@code revokedAt} for
        //    one access-token TTL window. The gateway compares iat (epoch
        //    seconds × 1000) against this epoch-millis with {@code <=}, so
        //    tokens issued in the same second as the reset are also rejected.
        //    Fail-soft is the store's responsibility — see
        //    {@link AccessTokenInvalidationStore}.
        accessTokenInvalidationStore.invalidateAccessBefore(
                accountId, revokedAt, tokenGeneratorPort.accessTokenTtlSeconds());

        // 7) Single-use enforcement: delete the token AFTER all DB writes have
        //    succeeded. If any of steps 4–5 fails the transaction rolls back
        //    and the token must remain valid for retry.
        passwordResetTokenStore.delete(command.token());

        log.info("Password reset confirmed for accountId={}, revokedTokens={}, sasAuthorizations={}",
                accountId, legacyRevoked, sasRevoked);

        // 8) TASK-BE-612: self-recovery of an AUTO_DETECT lock — only once the reset is COMMITTED.
        //    A reset that rolls back must not have unlocked anything.
        afterCommit(() -> recoverSelfRecoverableLock(accountId));
    }

    /**
     * TASK-BE-612 (owner decision, TASK-BE-608 § AC-3): a confirmed reset — proof of mailbox ownership
     * plus every session revoked — lifts a lock that auto-detection put on. It asks account-service to
     * unlock with {@code USER_RECOVERY} when the account is LOCKED; account-service decides whether the
     * lock is self-recoverable (only an {@code AUTO_DETECT} lock is) and answers 409 otherwise.
     *
     * <p>🔴 <b>Fail-soft (AC-4).</b> The unlock is best effort: the reset's own effects — the new
     * password and the revoked sessions — are already committed and are never undone or reported as
     * an error because the unlock could not be done. A failure is logged; the user can reset again or
     * be unlocked by an operator.
     */
    void recoverSelfRecoverableLock(String accountId) {
        try {
            boolean locked = accountServicePort.getAccountStatusAndTenant(accountId)
                    .map(s -> "LOCKED".equals(s.accountStatus()))
                    .orElse(false);
            if (!locked) {
                return; // ACTIVE (the usual case) or unknown — nothing to recover, no call
            }
            AccountServicePort.SelfRecoveryUnlock outcome = accountServicePort.unlockForSelfRecovery(accountId);
            log.info("Password reset self-recovery for accountId={}: {}", accountId, outcome);
        } catch (RuntimeException e) {
            log.warn("Password reset self-recovery unlock skipped (fail-soft) for accountId={}: {}",
                    accountId, e.getMessage());
        }
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run(); // no surrounding transaction (e.g. a plain unit call) — nothing to wait for
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
