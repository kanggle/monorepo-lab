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
 * Unit tests for {@link CredentialAuthenticationProvider} focused on TASK-BE-329
 * (ADR-MONO-021 D1/D3): the authenticated principal's details map must carry the
 * per-account {@code account_type} alongside {@code tenant_id}/{@code tenant_type}/
 * {@code account_id}, so {@code TenantClaimTokenCustomizer} can emit the claim.
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

    private Credential credential(String accountType) {
        Instant now = Instant.parse("2026-06-02T00:00:00Z");
        return new Credential(
                1L, "acc-1", "acme-corp", accountType, EMAIL,
                "$argon2id$stored-hash", "argon2id", now, now, 0);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> authenticateAndGetDetails(String accountType) {
        when(credentialRepository.findAllByEmail(EMAIL)).thenReturn(List.of(credential(accountType)));
        when(passwordHasher.verify(PASSWORD, "$argon2id$stored-hash")).thenReturn(true);

        Authentication result = provider.authenticate(
                new UsernamePasswordAuthenticationToken(EMAIL, PASSWORD));

        return (Map<String, Object>) result.getDetails();
    }

    @Test
    @DisplayName("details map carries account_type=OPERATOR from the resolved credential")
    void detailsMap_carriesOperatorAccountType() {
        Map<String, Object> details = authenticateAndGetDetails("OPERATOR");

        assertThat(details).containsEntry("account_type", "OPERATOR");
        // existing keys preserved
        assertThat(details).containsEntry("tenant_id", "acme-corp");
        assertThat(details).containsKey("tenant_type");
        assertThat(details).containsEntry("account_id", "acc-1");
    }

    @Test
    @DisplayName("details map carries account_type=CONSUMER for a consumer credential")
    void detailsMap_carriesConsumerAccountType() {
        Map<String, Object> details = authenticateAndGetDetails("CONSUMER");

        assertThat(details).containsEntry("account_type", "CONSUMER");
    }
}
