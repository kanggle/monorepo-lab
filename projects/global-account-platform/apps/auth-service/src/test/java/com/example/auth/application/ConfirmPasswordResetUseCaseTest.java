package com.example.auth.application;

import com.example.auth.application.command.ConfirmPasswordResetCommand;
import com.example.auth.application.exception.PasswordResetTokenInvalidException;
import com.example.auth.application.port.TokenGeneratorPort;
import com.example.auth.domain.credentials.Credential;
import com.example.auth.domain.credentials.PasswordPolicyViolationException;
import com.example.auth.domain.repository.AccessTokenInvalidationStore;
import com.example.auth.domain.repository.BulkInvalidationStore;
import com.example.auth.domain.repository.CredentialRepository;
import com.example.auth.domain.repository.PasswordResetTokenStore;
import com.example.auth.domain.repository.RefreshTokenRepository;
import com.gap.security.password.PasswordHasher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConfirmPasswordResetUseCase unit tests")
class ConfirmPasswordResetUseCaseTest {

    private static final String TOKEN = "reset-token-uuid";
    private static final String ACCOUNT_ID = "acc-1";
    private static final long REFRESH_TTL = 604_800L;
    private static final long ACCESS_TTL = 1_800L;

    @Mock
    private PasswordResetTokenStore passwordResetTokenStore;

    @Mock
    private CredentialRepository credentialRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private BulkInvalidationStore bulkInvalidationStore;

    @Mock
    private AccessTokenInvalidationStore accessTokenInvalidationStore;

    @Mock
    private TokenGeneratorPort tokenGeneratorPort;

    @Mock
    private PasswordHasher passwordHasher;

    @InjectMocks
    private ConfirmPasswordResetUseCase useCase;

    private Credential existingCredential() {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        return new Credential(
                42L,
                ACCOUNT_ID,
                "user@example.com",
                "$argon2id$v=19$old-hash",
                "argon2id",
                created,
                created,
                3
        );
    }

    @Test
    @DisplayName("execute_validToken_updatesHashAndRevokesAllSessions — happy path: hash + revoke + delete in correct order")
    void execute_validToken_updatesHashAndRevokesAllSessions() {
        Credential existing = existingCredential();
        ConfirmPasswordResetCommand cmd =
                new ConfirmPasswordResetCommand(TOKEN, "NewPassw0rd!");

        given(passwordResetTokenStore.findAccountId(TOKEN)).willReturn(Optional.of(ACCOUNT_ID));
        given(credentialRepository.findByAccountId(ACCOUNT_ID)).willReturn(Optional.of(existing));
        given(passwordHasher.hash("NewPassw0rd!")).willReturn("$argon2id$v=19$new-hash");
        given(credentialRepository.save(any(Credential.class)))
                .willAnswer(inv -> inv.getArgument(0));
        given(refreshTokenRepository.revokeAllByAccountId(ACCOUNT_ID)).willReturn(2);
        given(tokenGeneratorPort.refreshTokenTtlSeconds()).willReturn(REFRESH_TTL);
        given(tokenGeneratorPort.accessTokenTtlSeconds()).willReturn(ACCESS_TTL);

        Instant before = Instant.now();
        useCase.execute(cmd);
        Instant after = Instant.now();

        // The new credential is persisted with the freshly hashed value.
        ArgumentCaptor<Credential> savedCaptor = ArgumentCaptor.forClass(Credential.class);
        verify(credentialRepository).save(savedCaptor.capture());
        Credential saved = savedCaptor.getValue();
        assertThat(saved.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(saved.getEmail()).isEqualTo("user@example.com");
        assertThat(saved.getCredentialHash()).isEqualTo("$argon2id$v=19$new-hash");
        assertThat(saved.getHashAlgorithm()).isEqualTo("argon2id");
        assertThat(saved.getVersion()).isEqualTo(existing.getVersion() + 1);
        assertThat(saved.getCreatedAt()).isEqualTo(existing.getCreatedAt());

        // Critical ordering: save → revoke (refresh + bulk + access marker) → delete token last.
        // If save or revoke fails the token must still be valid for retry.
        InOrder order = inOrder(
                credentialRepository, refreshTokenRepository,
                bulkInvalidationStore, accessTokenInvalidationStore, passwordResetTokenStore);
        order.verify(credentialRepository).save(any(Credential.class));
        order.verify(refreshTokenRepository).revokeAllByAccountId(ACCOUNT_ID);
        order.verify(bulkInvalidationStore).invalidateAll(ACCOUNT_ID, REFRESH_TTL);

        // TASK-BE-146: marker is written via the port with the access-token TTL,
        // and the timestamp lies inside the test's wall-clock window.
        ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
        order.verify(accessTokenInvalidationStore).invalidateAccessBefore(
                eq(ACCOUNT_ID), instantCaptor.capture(), eq(ACCESS_TTL));
        assertThat(instantCaptor.getValue())
                .isBetween(before, after);

        order.verify(passwordResetTokenStore).delete(TOKEN);
    }

    @Test
    @DisplayName("execute_unknownToken_throws — no account mapping → PasswordResetTokenInvalidException, no side-effects")
    void execute_unknownToken_throws() {
        ConfirmPasswordResetCommand cmd =
                new ConfirmPasswordResetCommand(TOKEN, "NewPassw0rd!");

        given(passwordResetTokenStore.findAccountId(TOKEN)).willReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(cmd))
                .isInstanceOf(PasswordResetTokenInvalidException.class);

        // Argon2id hashing is expensive — must not run when token already failed.
        verify(passwordHasher, never()).hash(any());
        verify(credentialRepository, never()).findByAccountId(anyString());
        verify(credentialRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revokeAllByAccountId(anyString());
        verify(bulkInvalidationStore, never()).invalidateAll(anyString(), anyLong());
        verifyNoInteractions(accessTokenInvalidationStore);
        // Critically: token MUST NOT be deleted on a failed attempt.
        verify(passwordResetTokenStore, never()).delete(anyString());
    }

    @Test
    @DisplayName("execute_credentialMissing_throws — token resolves but credential row gone → same exception, token preserved")
    void execute_credentialMissing_throws() {
        ConfirmPasswordResetCommand cmd =
                new ConfirmPasswordResetCommand(TOKEN, "NewPassw0rd!");

        given(passwordResetTokenStore.findAccountId(TOKEN)).willReturn(Optional.of(ACCOUNT_ID));
        given(credentialRepository.findByAccountId(ACCOUNT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(cmd))
                .isInstanceOf(PasswordResetTokenInvalidException.class);

        verify(passwordHasher, never()).hash(any());
        verify(credentialRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revokeAllByAccountId(anyString());
        verify(bulkInvalidationStore, never()).invalidateAll(anyString(), anyLong());
        verifyNoInteractions(accessTokenInvalidationStore);
        verify(passwordResetTokenStore, never()).delete(anyString());
    }

    @Test
    @DisplayName("execute_policyViolation_throwsAndDoesNotDelete — policy fails → no hash, no revoke, no delete")
    void execute_policyViolation_throwsAndDoesNotDelete() {
        Credential existing = existingCredential();
        // newPassword too short (< 8 chars) — fails PasswordPolicy.
        ConfirmPasswordResetCommand cmd =
                new ConfirmPasswordResetCommand(TOKEN, "short");

        given(passwordResetTokenStore.findAccountId(TOKEN)).willReturn(Optional.of(ACCOUNT_ID));
        given(credentialRepository.findByAccountId(ACCOUNT_ID)).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> useCase.execute(cmd))
                .isInstanceOf(PasswordPolicyViolationException.class);

        verify(passwordHasher, never()).hash(any());
        verify(credentialRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revokeAllByAccountId(anyString());
        verify(bulkInvalidationStore, never()).invalidateAll(anyString(), anyLong());
        verifyNoInteractions(accessTokenInvalidationStore);
        // Token preservation lets the user retry with a compliant password.
        verify(passwordResetTokenStore, never()).delete(anyString());
    }
}
