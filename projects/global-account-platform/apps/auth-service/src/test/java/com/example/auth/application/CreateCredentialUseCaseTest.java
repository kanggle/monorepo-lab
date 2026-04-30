package com.example.auth.application;

import com.example.auth.application.command.CreateCredentialCommand;
import com.example.auth.application.exception.CredentialAlreadyExistsException;
import com.example.auth.application.result.CreateCredentialResult;
import com.example.auth.domain.credentials.Credential;
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
    @DisplayName("Pre-check existsByAccountId → throws CredentialAlreadyExistsException without hashing")
    void duplicateAccountIdShortCircuits() {
        CreateCredentialCommand cmd = new CreateCredentialCommand(
                "acc-dup", "dup@example.com", "password123");
        given(credentialRepository.existsByAccountId("acc-dup")).willReturn(true);

        assertThatThrownBy(() -> useCase.execute(cmd))
                .isInstanceOf(CredentialAlreadyExistsException.class)
                .hasMessageContaining("already exists");

        // argon2id is expensive — must NOT be invoked when we can short-circuit on PK
        verify(passwordHasher, never()).hash(org.mockito.ArgumentMatchers.anyString());
        verify(credentialRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Concurrent insert race → DataIntegrityViolation translated to 409")
    void concurrentInsertRaceProducesConflict() {
        CreateCredentialCommand cmd = new CreateCredentialCommand(
                "acc-race", "race@example.com", "password123");
        given(credentialRepository.existsByAccountId("acc-race")).willReturn(false);
        given(passwordHasher.hash("password123")).willReturn("$argon2id$hash");
        given(credentialRepository.save(org.mockito.ArgumentMatchers.any(Credential.class)))
                .willThrow(new DataIntegrityViolationException("uk_credentials_account_id"));

        assertThatThrownBy(() -> useCase.execute(cmd))
                .isInstanceOf(CredentialAlreadyExistsException.class);
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
