package com.example.auth.application;

import com.example.auth.application.command.ChangePasswordCommand;
import com.example.auth.application.exception.CredentialsInvalidException;
import com.example.auth.application.exception.CurrentPasswordMismatchException;
import com.example.auth.domain.credentials.Credential;
import com.example.auth.domain.credentials.CredentialHash;
import com.example.auth.domain.credentials.PasswordPolicyViolationException;
import com.example.auth.domain.repository.CredentialRepository;
import com.example.security.password.PasswordHasher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChangePasswordUseCase unit tests")
class ChangePasswordUseCaseTest {

    @Mock
    private CredentialRepository credentialRepository;

    @Mock
    private PasswordHasher passwordHasher;

    @InjectMocks
    private ChangePasswordUseCase useCase;

    private Credential existingCredential() {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        return new Credential(
                42L,
                "acc-1",
                "user@example.com",
                "$argon2id$v=19$old-hash",
                "argon2id",
                created,
                created,
                3
        );
    }

    @Test
    @DisplayName("execute_validRequest_updatesHash — saves a new credential with the freshly hashed value")
    void execute_validRequest_updatesHash() {
        Credential existing = existingCredential();
        ChangePasswordCommand cmd = new ChangePasswordCommand(
                "acc-1", "OldPassw0rd!", "NewPassw0rd!");

        given(credentialRepository.findByAccountId("acc-1")).willReturn(Optional.of(existing));
        given(passwordHasher.verify("OldPassw0rd!", "$argon2id$v=19$old-hash")).willReturn(true);
        given(passwordHasher.hash("NewPassw0rd!")).willReturn("$argon2id$v=19$new-hash");
        given(credentialRepository.save(any(Credential.class)))
                .willAnswer(inv -> inv.getArgument(0));

        useCase.execute(cmd);

        ArgumentCaptor<Credential> captor = ArgumentCaptor.forClass(Credential.class);
        verify(credentialRepository).save(captor.capture());
        Credential saved = captor.getValue();
        assertThat(saved.getAccountId()).isEqualTo("acc-1");
        assertThat(saved.getEmail()).isEqualTo("user@example.com");
        assertThat(saved.getCredentialHash()).isEqualTo("$argon2id$v=19$new-hash");
        assertThat(saved.getHashAlgorithm()).isEqualTo("argon2id");
        assertThat(saved.getVersion()).isEqualTo(existing.getVersion() + 1);
        assertThat(saved.getCreatedAt()).isEqualTo(existing.getCreatedAt());
        assertThat(saved.getUpdatedAt()).isAfterOrEqualTo(existing.getUpdatedAt());
    }

    @Test
    @DisplayName("execute_wrongCurrentPassword_throws — verify() returns false → CredentialsInvalidException")
    void execute_wrongCurrentPassword_throws() {
        Credential existing = existingCredential();
        ChangePasswordCommand cmd = new ChangePasswordCommand(
                "acc-1", "WrongPassw0rd!", "NewPassw0rd!");

        given(credentialRepository.findByAccountId("acc-1")).willReturn(Optional.of(existing));
        given(passwordHasher.verify("WrongPassw0rd!", "$argon2id$v=19$old-hash"))
                .willReturn(false);

        assertThatThrownBy(() -> useCase.execute(cmd))
                .isInstanceOf(CurrentPasswordMismatchException.class)
                .isInstanceOf(CredentialsInvalidException.class);

        verify(passwordHasher, never()).hash(any());
        verify(credentialRepository, never()).save(any());
    }

    @Test
    @DisplayName("execute_policyViolation_throws — current password matches but new password fails policy")
    void execute_policyViolation_throws() {
        Credential existing = existingCredential();
        // newPassword too short (< 8 chars) — fails PasswordPolicy
        ChangePasswordCommand cmd = new ChangePasswordCommand(
                "acc-1", "OldPassw0rd!", "short");

        given(credentialRepository.findByAccountId("acc-1")).willReturn(Optional.of(existing));
        given(passwordHasher.verify("OldPassw0rd!", "$argon2id$v=19$old-hash")).willReturn(true);

        assertThatThrownBy(() -> useCase.execute(cmd))
                .isInstanceOf(PasswordPolicyViolationException.class);

        // Argon2id hashing is expensive — must not run when policy already failed.
        verify(passwordHasher, never()).hash(any());
        verify(credentialRepository, never()).save(any());
    }

    @Test
    @DisplayName("execute_credentialNotFound_throws — findByAccountId empty → CredentialsInvalidException")
    void execute_credentialNotFound_throws() {
        ChangePasswordCommand cmd = new ChangePasswordCommand(
                "acc-missing", "OldPassw0rd!", "NewPassw0rd!");

        given(credentialRepository.findByAccountId("acc-missing")).willReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(cmd))
                .isInstanceOf(CredentialsInvalidException.class);

        verify(passwordHasher, never()).verify(any(), any());
        verify(passwordHasher, never()).hash(any());
        verify(credentialRepository, never()).save(any());
    }
}
