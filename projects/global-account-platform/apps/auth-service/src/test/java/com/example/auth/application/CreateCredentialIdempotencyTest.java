package com.example.auth.application;

import com.example.auth.application.command.CreateCredentialCommand;
import com.example.auth.application.exception.CredentialAlreadyExistsException;
import com.example.auth.application.result.CreateCredentialResult;
import com.example.auth.domain.credentials.Credential;
import com.example.auth.domain.credentials.CredentialHash;
import com.example.auth.domain.repository.CredentialRepository;
import com.example.security.password.PasswordHasher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * TASK-BE-247: unit tests for the idempotency behavior of {@link CreateCredentialUseCase}.
 *
 * <p>Idempotency rule: if a credential already exists for the given accountId AND its stored email
 * matches the request email → return success (200 semantics, wasIdempotent=true). If the emails
 * differ → 409 (genuine conflict). Passwords are never compared.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("CreateCredentialUseCase — TASK-BE-247 idempotency")
class CreateCredentialIdempotencyTest {

    @Mock
    private CredentialRepository credentialRepository;

    @Mock
    private PasswordHasher passwordHasher;

    @InjectMocks
    private CreateCredentialUseCase useCase;

    private static final Instant FIXED_NOW = Instant.parse("2026-04-30T10:00:00Z");

    private Credential existingCredential(String accountId, String email) {
        return new Credential(
                42L, accountId, "fan-platform", email,
                "$argon2id$already-hashed", "argon2id",
                FIXED_NOW, FIXED_NOW, 0
        );
    }

    // --------------------------------------------------------------------------
    // 1st call — creates a new row (201)
    // --------------------------------------------------------------------------

    @Test
    @DisplayName("1st call: no existing row → creates credential, wasIdempotent=false")
    void firstCall_createsNewCredential() {
        CreateCredentialCommand cmd = new CreateCredentialCommand(
                "acc-1", "user@example.com", "password123");
        given(credentialRepository.existsByAccountId("acc-1")).willReturn(false);
        given(passwordHasher.hash("password123")).willReturn("$argon2id$v=19$hash");
        given(credentialRepository.save(any(Credential.class)))
                .willAnswer(inv -> inv.getArgument(0));

        CreateCredentialResult result = useCase.execute(cmd);

        assertThat(result.accountId()).isEqualTo("acc-1");
        assertThat(result.wasIdempotent()).isFalse();
        verify(credentialRepository).save(any(Credential.class));
    }

    // --------------------------------------------------------------------------
    // 2nd call (same accountId + email) → idempotent success (200)
    // --------------------------------------------------------------------------

    @Test
    @DisplayName("2nd call: same accountId + email → idempotent success, wasIdempotent=true, no new row")
    void secondCall_sameAccountIdAndEmail_idempotentSuccess() {
        CreateCredentialCommand cmd = new CreateCredentialCommand(
                "acc-1", "user@example.com", "password123");
        given(credentialRepository.existsByAccountId("acc-1")).willReturn(true);
        given(credentialRepository.findByAccountId("acc-1"))
                .willReturn(Optional.of(existingCredential("acc-1", "user@example.com")));

        CreateCredentialResult result = useCase.execute(cmd);

        assertThat(result.accountId()).isEqualTo("acc-1");
        assertThat(result.createdAt()).isEqualTo(FIXED_NOW);
        assertThat(result.wasIdempotent()).isTrue();

        // No new row should be inserted and password hashing must be skipped (expensive)
        verify(credentialRepository, never()).save(any(Credential.class));
        verify(passwordHasher, never()).hash(any());
    }

    @Test
    @DisplayName("Idempotent path: email comparison is case-insensitive after normalization")
    void secondCall_emailNormalization_idempotentSuccess() {
        // Stored email is already lowercase; request comes with mixed case
        CreateCredentialCommand cmd = new CreateCredentialCommand(
                "acc-2", "  User@EXAMPLE.COM  ", "password123");
        given(credentialRepository.existsByAccountId("acc-2")).willReturn(true);
        given(credentialRepository.findByAccountId("acc-2"))
                .willReturn(Optional.of(existingCredential("acc-2", "user@example.com")));

        CreateCredentialResult result = useCase.execute(cmd);

        assertThat(result.wasIdempotent()).isTrue();
    }

    // --------------------------------------------------------------------------
    // 3rd call: same accountId, different email → 409
    // --------------------------------------------------------------------------

    @Test
    @DisplayName("3rd call: same accountId, different email → 409 CREDENTIAL_ALREADY_EXISTS")
    void thirdCall_sameAccountIdDifferentEmail_conflict() {
        CreateCredentialCommand cmd = new CreateCredentialCommand(
                "acc-1", "other@example.com", "password123");
        given(credentialRepository.existsByAccountId("acc-1")).willReturn(true);
        given(credentialRepository.findByAccountId("acc-1"))
                .willReturn(Optional.of(existingCredential("acc-1", "user@example.com")));

        assertThatThrownBy(() -> useCase.execute(cmd))
                .isInstanceOf(CredentialAlreadyExistsException.class);

        verify(credentialRepository, never()).save(any(Credential.class));
        verify(passwordHasher, never()).hash(any());
    }

    // --------------------------------------------------------------------------
    // 4th call: different accountId, same email → constraint violation → 409
    // --------------------------------------------------------------------------

    @Test
    @DisplayName("4th call: different accountId, same email → DataIntegrityViolation → 409 when email mismatch")
    void fourthCall_differentAccountIdSameEmail_conflict() {
        CreateCredentialCommand cmd = new CreateCredentialCommand(
                "acc-new", "user@example.com", "password123");
        given(credentialRepository.existsByAccountId("acc-new")).willReturn(false);
        given(passwordHasher.hash("password123")).willReturn("$argon2id$v=19$hash");
        given(credentialRepository.save(any(Credential.class)))
                .willThrow(new org.springframework.dao.DataIntegrityViolationException("uk_email"));
        // The conflict recovery lookup finds a row for a DIFFERENT accountId  — email still matches
        // the stored row's email, but the stored row belongs to a different accountId. Since we
        // look up by the COMMAND's accountId (acc-new) and the row doesn't exist there, we get
        // Optional.empty() → 409.
        given(credentialRepository.findByAccountId("acc-new")).willReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(cmd))
                .isInstanceOf(CredentialAlreadyExistsException.class);
    }

    // --------------------------------------------------------------------------
    // Password is NOT compared — only (accountId, email) determines idempotency
    // --------------------------------------------------------------------------

    @Test
    @DisplayName("Idempotency: different password does NOT change result — passwords are never compared")
    void idempotency_differentPasswordIgnored() {
        // Same accountId + email, but caller passes a different password this time.
        // The system must still return idempotent success — comparing passwords would be both
        // incorrect (argon2id is non-deterministic) and a security regression.
        CreateCredentialCommand cmd = new CreateCredentialCommand(
                "acc-1", "user@example.com", "totally-different-password");
        given(credentialRepository.existsByAccountId("acc-1")).willReturn(true);
        given(credentialRepository.findByAccountId("acc-1"))
                .willReturn(Optional.of(existingCredential("acc-1", "user@example.com")));

        CreateCredentialResult result = useCase.execute(cmd);

        assertThat(result.wasIdempotent()).isTrue();
        verify(passwordHasher, never()).hash(any());
    }
}
