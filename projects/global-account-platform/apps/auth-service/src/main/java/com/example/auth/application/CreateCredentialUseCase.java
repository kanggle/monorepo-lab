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

    @Transactional
    public CreateCredentialResult execute(CreateCredentialCommand command) {
        String accountId = command.accountId();
        String email = Credential.normalizeEmail(command.email());
        String tenantId = command.tenantId() != null ? command.tenantId() : "fan-platform";

        if (credentialRepository.existsByAccountId(accountId)) {
            throw new CredentialAlreadyExistsException(accountId);
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
            throw new CredentialAlreadyExistsException(accountId);
        }
    }
}
