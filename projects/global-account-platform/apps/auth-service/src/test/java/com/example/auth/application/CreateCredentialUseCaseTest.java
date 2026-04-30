package com.example.auth.application;

import com.example.auth.application.command.CreateCredentialCommand;
import com.example.auth.application.exception.CredentialAlreadyExistsException;
import com.example.auth.application.result.CreateCredentialResult;
import com.example.auth.domain.credentials.Credential;
import com.example.auth.domain.credentials.CredentialHash;
import com.example.auth.domain.repository.CredentialRepository;
import com.gap.security.password.PasswordHasher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateCredentialUseCase unit tests")
class CreateCredentialUseCaseTest {

    @Mock
    private CredentialRepository credentialRepository;

    @Mock
    private PasswordHasher passwordHasher;

    @InjectMocks
    private CreateCredentialUseCase useCase;

    @Test
    @DisplayName("Persists a new credential for a never-seen accountId")
    void createsNewCredential() {
        // given
        CreateCredentialCommand cmd = new CreateCredentialCommand(
                "acc-1", "  Test@Example.COM ", "password123");
        given(credentialRepository.existsByAccountId("acc-1")).willReturn(false);
        given(passwordHasher.hash("password123")).willReturn("$argon2id$v=19$hashed");
        given(credentialRepository.save(org.mockito.ArgumentMatchers.any(Credential.class)))
                .willAnswer(inv -> inv.getArgument(0));

        // when
        CreateCredentialResult result = useCase.execute(cmd);

        // then — email normalized, hash delegated to PasswordHasher
        ArgumentCaptor<Credential> captor = ArgumentCaptor.forClass(Credential.class);
        verify(credentialRepository).save(captor.capture());
        Credential saved = captor.getValue();
        assertThat(saved.getAccountId()).isEqualTo("acc-1");
        assertThat(saved.getEmail()).isEqualTo("test@example.com");
        assertThat(saved.getCredentialHash()).isEqualTo("$argon2id$v=19$hashed");
        assertThat(saved.getHashAlgorithm()).isEqualTo("argon2id");

        assertThat(result.accountId()).isEqualTo("acc-1");
        assertThat(result.createdAt()).isNotNull();
    }

    @Test
    @DisplayName("Pre-check existsByAccountId with DIFFERENT email → throws CredentialAlreadyExistsException without hashing")
    void duplicateAccountIdShortCircuits() {
        CreateCredentialCommand cmd = new CreateCredentialCommand(
                "acc-dup", "dup@example.com", "password123");
        given(credentialRepository.existsByAccountId("acc-dup")).willReturn(true);
        // TASK-BE-247: existsByAccountId=true now triggers findByAccountId to determine idempotency.
        // Simulate a genuine conflict: the stored email is DIFFERENT from the request email.
        Credential existingWithDifferentEmail = new Credential(
                1L, "acc-dup", "fan-platform", "other@example.com",
                "$argon2id$stored", "argon2id",
                Instant.now(), Instant.now(), 0);
        given(credentialRepository.findByAccountId("acc-dup"))
                .willReturn(Optional.of(existingWithDifferentEmail));

        assertThatThrownBy(() -> useCase.execute(cmd))
                .isInstanceOf(CredentialAlreadyExistsException.class)
                .hasMessageContaining("already exists");

        // argon2id is expensive — must NOT be invoked when we can short-circuit on PK
        verify(passwordHasher, never()).hash(org.mockito.ArgumentMatchers.anyString());
        verify(credentialRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Concurrent insert race (email collision) → DataIntegrityViolation → 409 when no matching accountId row found")
    void concurrentInsertRaceProducesConflict() {
        // Race on the email unique constraint: a different accountId committed the same email first.
        // Our accountId "acc-race" has no row → findByAccountId returns empty → 409.
        CreateCredentialCommand cmd = new CreateCredentialCommand(
                "acc-race", "race@example.com", "password123");
        given(credentialRepository.existsByAccountId("acc-race")).willReturn(false);
        given(passwordHasher.hash("password123")).willReturn("$argon2id$hash");
        given(credentialRepository.save(org.mockito.ArgumentMatchers.any(Credential.class)))
                .willThrow(new DataIntegrityViolationException("uk_credentials_email"));
        // TASK-BE-247: after the race, we attempt idempotent resolution. No row for our accountId
        // means the conflict was on a different accountId's email → genuine 409.
        given(credentialRepository.findByAccountId("acc-race")).willReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(cmd))
                .isInstanceOf(CredentialAlreadyExistsException.class);
    }

    @Test
    @DisplayName("Concurrent insert race (accountId collision) → DataIntegrityViolation → idempotent 200 when emails match")
    void concurrentInsertRaceWithSameEmailProducesIdempotentResult() {
        // Race: two concurrent requests for the same (accountId, email). Both pass the
        // existsByAccountId=false check, one wins the insert, the other gets DataIntegrityViolation.
        // The loser should resolve idempotently.
        CreateCredentialCommand cmd = new CreateCredentialCommand(
                "acc-concurrent", "concurrent@example.com", "password123");
        given(credentialRepository.existsByAccountId("acc-concurrent")).willReturn(false);
        given(passwordHasher.hash("password123")).willReturn("$argon2id$hash");
        given(credentialRepository.save(org.mockito.ArgumentMatchers.any(Credential.class)))
                .willThrow(new DataIntegrityViolationException("uk_credentials_account_id"));
        Credential winner = new Credential(
                99L, "acc-concurrent", "fan-platform", "concurrent@example.com",
                "$argon2id$winner-hash", "argon2id",
                Instant.now(), Instant.now(), 0);
        given(credentialRepository.findByAccountId("acc-concurrent"))
                .willReturn(Optional.of(winner));

        CreateCredentialResult result = useCase.execute(cmd);

        assertThat(result.accountId()).isEqualTo("acc-concurrent");
        assertThat(result.wasIdempotent()).isTrue();
    }

    @Test
    @DisplayName("Credential carries current timestamp on creation")
    void timestampIsRecent() {
        CreateCredentialCommand cmd = new CreateCredentialCommand(
                "acc-2", "a@b.co", "password123");
        given(credentialRepository.existsByAccountId("acc-2")).willReturn(false);
        given(passwordHasher.hash("password123")).willReturn("$argon2id$x");
        given(credentialRepository.save(org.mockito.ArgumentMatchers.any(Credential.class)))
                .willAnswer(inv -> inv.getArgument(0));

        Instant before = Instant.now();
        CreateCredentialResult result = useCase.execute(cmd);
        Instant after = Instant.now();

        assertThat(result.createdAt()).isBetween(before.minusSeconds(1), after.plusSeconds(1));
    }
}
