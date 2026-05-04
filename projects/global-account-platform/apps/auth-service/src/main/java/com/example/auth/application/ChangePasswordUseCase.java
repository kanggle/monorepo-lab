package com.example.auth.application;

import com.example.auth.application.command.ChangePasswordCommand;
import com.example.auth.application.exception.CredentialsInvalidException;
import com.example.auth.application.exception.CurrentPasswordMismatchException;
import com.example.auth.domain.credentials.Credential;
import com.example.auth.domain.credentials.CredentialHash;
import com.example.auth.domain.credentials.PasswordPolicy;
import com.example.auth.domain.repository.CredentialRepository;
import com.example.security.password.PasswordHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Use case for {@code PATCH /api/auth/password} — change the authenticated
 * caller's password after verifying the current one and enforcing the
 * password policy.
 *
 * <p>R4 (rules/traits/regulated.md): plaintext passwords from the command
 * are never logged. The only logging here is at INFO level and identifies
 * only the {@code accountId}.</p>
 *
 * <p>Failure modes:
 * <ul>
 *   <li>credential row not found → {@link CredentialsInvalidException} (no
 *       enumeration of which side mismatched per {@code auth-api.md})</li>
 *   <li>{@code currentPassword} does not match stored hash →
 *       {@link CurrentPasswordMismatchException} (mapped to 400
 *       {@code CREDENTIALS_INVALID})</li>
 *   <li>{@code newPassword} fails {@link PasswordPolicy} →
 *       {@code PasswordPolicyViolationException} (mapped to 400
 *       {@code PASSWORD_POLICY_VIOLATION})</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChangePasswordUseCase {

    private final CredentialRepository credentialRepository;
    private final PasswordHasher passwordHasher;

    @Transactional
    public void execute(ChangePasswordCommand command) {
        Credential credential = credentialRepository.findByAccountId(command.accountId())
                .orElseThrow(CredentialsInvalidException::new);

        if (!passwordHasher.verify(command.currentPassword(), credential.getCredentialHash())) {
            throw new CurrentPasswordMismatchException();
        }

        // Throws PasswordPolicyViolationException on violation; that exception
        // never echoes the plaintext password back so logging the message is
        // safe at the global handler level.
        PasswordPolicy.validate(command.newPassword(), credential.getEmail());

        String newHash = passwordHasher.hash(command.newPassword());
        Credential updated = credential.changePassword(
                CredentialHash.argon2id(newHash), Instant.now());
        credentialRepository.save(updated);

        log.info("Password changed for accountId={}", command.accountId());
    }
}
