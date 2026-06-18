package com.example.auth.application;

import com.example.auth.domain.credentials.Credential;
import com.example.auth.domain.repository.CredentialRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-MONO-295 (ADR-MONO-040 Phase 2) — unit tests for
 * {@link ResolveCredentialEmailUseCase}, the auth_db.credentials account_id → email
 * resolver that backs the internal endpoint the login-time operator-token exchange
 * (admin-service) consults for the DUAL-KEY email fallback.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("ResolveCredentialEmailUseCase 단위 테스트")
class ResolveCredentialEmailUseCaseTest {

    private static final String ACCOUNT_ID = "01928c4a-7e9f-7c00-9a40-d2b1f5e8c300";

    @Mock CredentialRepository credentialRepository;
    @InjectMocks ResolveCredentialEmailUseCase useCase;

    private Credential credential(String email) {
        return new Credential(1L, ACCOUNT_ID, email, "hash", "argon2id",
                Instant.now(), Instant.now(), 0);
    }

    @Test
    @DisplayName("credential row 존재 → 그 email 반환 (정규화된 lower-case)")
    void existingCredential_returnsEmail() {
        when(credentialRepository.findByAccountId(ACCOUNT_ID))
                .thenReturn(Optional.of(credential("operator@example.com")));

        assertThat(useCase.resolveEmail(ACCOUNT_ID)).contains("operator@example.com");
    }

    @Test
    @DisplayName("credential row 부재 → empty (graceful — caller resolves on account_id alone)")
    void noCredential_returnsEmpty() {
        when(credentialRepository.findByAccountId(ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThat(useCase.resolveEmail(ACCOUNT_ID)).isEmpty();
    }

    @Test
    @DisplayName("blank account_id → empty, repository 미조회")
    void blankAccountId_returnsEmptyWithoutLookup() {
        assertThat(useCase.resolveEmail("  ")).isEmpty();
        verify(credentialRepository, never()).findByAccountId(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("null account_id → empty, repository 미조회")
    void nullAccountId_returnsEmptyWithoutLookup() {
        assertThat(useCase.resolveEmail(null)).isEmpty();
        verify(credentialRepository, never()).findByAccountId(org.mockito.ArgumentMatchers.anyString());
    }
}
