package com.example.auth.application;

import com.example.auth.application.command.CreateCredentialCommand;
import com.example.auth.application.exception.CredentialAlreadyExistsException;
import com.example.auth.application.result.CreateCredentialResult;
import com.example.auth.domain.credentials.Credential;
import com.example.auth.domain.credentials.CredentialHash;
import com.example.auth.domain.repository.CredentialRepository;
import com.gap.security.password.PasswordHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Use case for creating a credential row in auth_db.credentials.
 *
 * <p>Called from the internal endpoint {@code POST /internal/auth/credentials},
 * which account-service invokes during signup (TASK-BE-063 Option A).</p>
 *
 * <p>TASK-BE-229: tenantId is now included in the credential row.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreateCredentialUseCase {

    private final CredentialRepository credentialRepository;
    private final PasswordHasher passwordHasher;

    /**
     * Creates a new credential row, or returns success if an identical (accountId, email) row
     * already exists (idempotent re-try path — TASK-BE-247).
     *
     * <p><b>Idempotency rule</b>: if a credential row already exists for the given accountId,
     * check whether its stored email matches the request email. If they match → return the
     * existing row's result as success (200 semantics). If they do not match → throw
     * {@link CredentialAlreadyExistsException} (409 semantics — genuine conflict).</p>
     *
     * <p>Passwords are NOT compared. The argon2id hash is salted and non-deterministic; comparing
     * hashes would require re-hashing the incoming plaintext which is an unnecessary CPU cost, and
     * comparing plaintext would be a security regression. The (accountId, email) pair is sufficient
     * to identify a retry of the same signup attempt.</p>
     */
    @Transactional
    public CreateCredentialResult execute(CreateCredentialCommand command) {
        String accountId = command.accountId();
        String email = Credential.normalizeEmail(command.email());
        String tenantId = command.tenantId() != null ? command.tenantId() : "fan-platform";

        // TASK-BE-247: idempotency — check existing row before hashing (argon2id is expensive)
        if (credentialRepository.existsByAccountId(accountId)) {
            return credentialRepository.findByAccountId(accountId)
                    .map(existing -> {
                        if (existing.getEmail().equals(email)) {
                            // Same (accountId, email) — this is a retry of the same signup attempt.
                            // Return success so account-service can complete the half-committed signup.
                            log.info("Idempotent credential create for accountId={} tenantId={} — returning existing row",
                                    accountId, tenantId);
                            return new CreateCredentialResult(existing.getAccountId(), existing.getCreatedAt(), true);
                        }
                        // Same accountId but different email — genuine conflict, reject.
                        throw new CredentialAlreadyExistsException(accountId);
                    })
                    .orElseThrow(() -> new CredentialAlreadyExistsException(accountId));
        }

        String hash = passwordHasher.hash(command.password());
        Instant now = Instant.now();
        Credential credential = Credential.create(
                accountId,
                tenantId,
                email,
                CredentialHash.argon2id(hash),
                now
        );

        try {
            Credential saved = credentialRepository.save(credential);
            log.info("Credential created for accountId={} tenantId={}", saved.getAccountId(),
                    saved.getTenantId());
            return new CreateCredentialResult(saved.getAccountId(), saved.getCreatedAt());
        } catch (DataIntegrityViolationException e) {
            // Concurrent insert race: another thread committed a row between the existsByAccountId
            // check and this save. Attempt idempotent resolution before failing.
            return credentialRepository.findByAccountId(accountId)
                    .filter(existing -> existing.getEmail().equals(email))
                    .map(existing -> new CreateCredentialResult(existing.getAccountId(), existing.getCreatedAt(), true))
                    .orElseThrow(() -> new CredentialAlreadyExistsException(accountId));
        }
    }
}
