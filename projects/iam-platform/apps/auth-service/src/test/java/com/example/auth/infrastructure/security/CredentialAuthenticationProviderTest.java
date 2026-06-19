package com.example.auth.infrastructure.security;

import com.example.auth.application.exception.AccountServiceUnavailableException;
import com.example.auth.domain.credentials.Credential;
import com.example.auth.application.port.TenantTypePort;
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
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CredentialAuthenticationProvider}.
 *
 * <p>TASK-MONO-263 (ADR-032 D5 step 4): the authenticated principal's details map
 * carries {@code tenant_id}/{@code tenant_type}/{@code account_id} but NO LONGER
 * carries {@code account_type} — the claim is removed entirely.
 *
 * <p>TASK-BE-407: {@code tenant_type} is now resolved from account-service via
 * {@link TenantTypePort} (no longer the hardcoded fallback). An account-service
 * outage must surface as {@link AuthenticationServiceException} (AC-5).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class CredentialAuthenticationProviderTest {

    @Mock
    private CredentialRepository credentialRepository;

    @Mock
    private PasswordHasher passwordHasher;

    @Mock
    private TenantTypePort tenantTypePort;

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

    private void stubHappyCredentialLookup() {
        when(credentialRepository.findAllByEmail(EMAIL)).thenReturn(List.of(credential()));
        when(passwordHasher.verify(PASSWORD, "$argon2id$stored-hash")).thenReturn(true);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> authenticateAndGetDetails() {
        Authentication result = provider.authenticate(
                new UsernamePasswordAuthenticationToken(EMAIL, PASSWORD));

        return (Map<String, Object>) result.getDetails();
    }

    @Test
    @DisplayName("details map carries tenant + account_id, but NOT account_type (MONO-263)")
    void detailsMap_noAccountType() {
        stubHappyCredentialLookup();
        when(tenantTypePort.resolve("acme-corp")).thenReturn("B2B_ENTERPRISE");

        Map<String, Object> details = authenticateAndGetDetails();

        assertThat(details).doesNotContainKey("account_type");
        assertThat(details).containsEntry("tenant_id", "acme-corp");
        assertThat(details).containsKey("tenant_type");
        assertThat(details).containsEntry("account_id", "acc-1");
    }

    @Test
    @DisplayName("TASK-BE-407: tenant_type in the details map is the resolver's authoritative value")
    void detailsMap_tenantTypeFromResolver() {
        stubHappyCredentialLookup();
        // A B2C tenant value that the OLD hardcoded fallback would have wrongly
        // produced as B2B_ENTERPRISE for a non-fan-platform tenant.
        when(tenantTypePort.resolve("acme-corp")).thenReturn("B2C_CONSUMER");

        Map<String, Object> details = authenticateAndGetDetails();

        assertThat(details).containsEntry("tenant_type", "B2C_CONSUMER");
    }

    @Test
    @DisplayName("AC-5: account-service outage → AuthenticationServiceException (not a raw RuntimeException)")
    void resolverUnavailable_throwsAuthenticationServiceException() {
        stubHappyCredentialLookup();
        when(tenantTypePort.resolve("acme-corp"))
                .thenThrow(new AccountServiceUnavailableException("down"));

        assertThatThrownBy(() -> provider.authenticate(
                new UsernamePasswordAuthenticationToken(EMAIL, PASSWORD)))
                .isInstanceOf(AuthenticationServiceException.class)
                .hasCauseInstanceOf(AccountServiceUnavailableException.class);
    }
}
