package com.example.auth.infrastructure.security;

import com.example.auth.domain.credentials.Credential;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CredentialAuthenticationProvider}.
 *
 * <p>TASK-MONO-263 (ADR-032 D5 step 4): the authenticated principal's details map
 * carries {@code tenant_id}/{@code tenant_type}/{@code account_id} but NO LONGER
 * carries {@code account_type} — the claim is removed entirely.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class CredentialAuthenticationProviderTest {

    @Mock
    private CredentialRepository credentialRepository;

    @Mock
    private PasswordHasher passwordHasher;

    @InjectMocks
    private CredentialAuthenticationProvider provider;

    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "secret123";

    private Credential credential() {
        Instant now = Instant.parse("2026-06-02T00:00:00Z");
        return new Credential(
                1L, "acc-1", "acme-corp", EMAIL,
                "$argon2id$stored-hash", "argon2id", now, now, 0);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> authenticateAndGetDetails() {
        when(credentialRepository.findAllByEmail(EMAIL)).thenReturn(List.of(credential()));
        when(passwordHasher.verify(PASSWORD, "$argon2id$stored-hash")).thenReturn(true);

        Authentication result = provider.authenticate(
                new UsernamePasswordAuthenticationToken(EMAIL, PASSWORD));

        return (Map<String, Object>) result.getDetails();
    }

    @Test
    @DisplayName("details map carries tenant + account_id, but NOT account_type (MONO-263)")
    void detailsMap_noAccountType() {
        Map<String, Object> details = authenticateAndGetDetails();

        assertThat(details).doesNotContainKey("account_type");
        assertThat(details).containsEntry("tenant_id", "acme-corp");
        assertThat(details).containsKey("tenant_type");
        assertThat(details).containsEntry("account_id", "acc-1");
    }
}
