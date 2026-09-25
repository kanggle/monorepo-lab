package com.example.auth.infrastructure.security;

import com.example.auth.application.LoginEventRecorder;
import com.example.auth.application.LoginHashes;
import com.example.auth.application.exception.AccountServiceUnavailableException;
import com.example.auth.domain.credentials.Credential;
import com.example.auth.application.port.TenantTypePort;
import com.example.auth.domain.repository.CredentialRepository;
import com.example.auth.domain.session.SessionContext;
import com.example.security.password.PasswordHasher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    @Mock
    private SavedRequestTenantResolver savedRequestTenantResolver;

    @Mock
    private LoginEventRecorder loginEventRecorder;

    @InjectMocks
    private CredentialAuthenticationProvider provider;

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

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
    @DisplayName("TASK-BE-577: the details map carries the email, so the customizer never has to read it off the principal name")
    void detailsMap_carriesEmail() {
        stubHappyCredentialLookup();
        when(tenantTypePort.resolve("acme-corp")).thenReturn("B2B_ENTERPRISE");

        Map<String, Object> details = authenticateAndGetDetails();

        // The producer↔consumer half of the email claim. The customizer reads this key;
        // if a producer stops publishing it, the claim silently disappears — which is
        // why the key lives in PrincipalDetailKeys and is asserted here rather than
        // being inferred from Authentication.getName() happening to be the same string.
        assertThat(details).containsEntry("email", EMAIL);
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

    // ---------------------------------------------------------------------------------
    // TASK-BE-599 — login events + device session on the form-login path
    // ---------------------------------------------------------------------------------

    private static final String EMAIL_HASH = LoginHashes.emailHash(EMAIL);

    private static Credential credentialIn(String tenantId) {
        Instant now = Instant.parse("2026-06-02T00:00:00Z");
        return new Credential(
                1L, "acc-1", tenantId, EMAIL,
                "$argon2id$stored-hash", "argon2id", now, now, 0);
    }

    /**
     * Binds a servlet request (as the form-login filter does) whose initiating OIDC client
     * resolves to {@code clientTenant} ({@code null} = no saved authorize request).
     */
    private MockHttpServletRequest bindRequest(String clientTenant) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.setRemoteAddr("203.0.113.7");
        request.addHeader("User-Agent", "Mozilla/5.0 Chrome/120.0");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
        when(savedRequestTenantResolver.resolve(request, response))
                .thenReturn(new SavedRequestTenantResolver.Resolution(clientTenant, null, null));
        return request;
    }

    private static UsernamePasswordAuthenticationToken attempt(String password) {
        return new UsernamePasswordAuthenticationToken(EMAIL, password);
    }

    @Test
    @DisplayName("BE-599: success → attempted + succeeded with accountId, the account's tenant, and the request context")
    void success_recordsAttemptedAndSucceeded() {
        bindRequest(null);
        stubHappyCredentialLookup();
        when(tenantTypePort.resolve("acme-corp")).thenReturn("B2B_ENTERPRISE");

        provider.authenticate(attempt(PASSWORD));

        ArgumentCaptor<SessionContext> ctx = ArgumentCaptor.forClass(SessionContext.class);
        verify(loginEventRecorder).recordAttempted(eq("acc-1"), eq(EMAIL_HASH), eq("acme-corp"), any());
        verify(loginEventRecorder).recordSucceeded(eq("acc-1"), eq("acme-corp"), ctx.capture());
        verify(loginEventRecorder, never()).recordFailed(any(), any(), any(), any(), any());
        // Derived exactly like every other auth-service entry point (SessionContexts).
        assertThat(ctx.getValue().ipMasked()).isEqualTo("203.0.*.*");
        assertThat(ctx.getValue().userAgentFamily()).isEqualTo("Chrome");
        assertThat(ctx.getValue().resolvedGeoCountry()).isEqualTo("XX");
    }

    @Test
    @DisplayName("BE-599 AC-2: wrong password → failed carries the resolved accountId and CREDENTIALS_INVALID; no success side effects")
    void wrongPassword_failedCarriesAccountId() {
        bindRequest(null);
        when(credentialRepository.findAllByEmail(EMAIL)).thenReturn(List.of(credential()));
        when(passwordHasher.verify("wrong", "$argon2id$stored-hash")).thenReturn(false);

        assertThatThrownBy(() -> provider.authenticate(attempt("wrong")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid credentials");

        verify(loginEventRecorder).recordAttempted(eq("acc-1"), eq(EMAIL_HASH), eq("acme-corp"), any());
        verify(loginEventRecorder).recordFailed(
                eq("acc-1"), eq(EMAIL_HASH), eq("acme-corp"), eq("CREDENTIALS_INVALID"), any());
        verify(loginEventRecorder, never()).recordSucceeded(any(), any(), any());
    }

    @Test
    @DisplayName("BE-599 AC-2: cross-tenant fallback → the failed event carries the ACCOUNT's tenant, not the initiating client's")
    void wrongPassword_crossTenantFallback_usesAccountTenant() {
        bindRequest("ecommerce");
        when(credentialRepository.findByTenantIdAndEmail("ecommerce", EMAIL)).thenReturn(Optional.empty());
        when(credentialRepository.findAllByEmail(EMAIL)).thenReturn(List.of(credentialIn("fan-platform")));
        when(passwordHasher.verify("wrong", "$argon2id$stored-hash")).thenReturn(false);

        assertThatThrownBy(() -> provider.authenticate(attempt("wrong")))
                .isInstanceOf(BadCredentialsException.class);

        verify(loginEventRecorder).recordAttempted(eq("acc-1"), eq(EMAIL_HASH), eq("fan-platform"), any());
        verify(loginEventRecorder).recordFailed(
                eq("acc-1"), eq(EMAIL_HASH), eq("fan-platform"), eq("CREDENTIALS_INVALID"), any());
    }

    @Test
    @DisplayName("BE-599: success through the cross-tenant fallback → succeeded carries the account's tenant")
    void success_crossTenantFallback_usesAccountTenant() {
        bindRequest("ecommerce");
        when(credentialRepository.findByTenantIdAndEmail("ecommerce", EMAIL)).thenReturn(Optional.empty());
        when(credentialRepository.findAllByEmail(EMAIL)).thenReturn(List.of(credentialIn("fan-platform")));
        when(passwordHasher.verify(PASSWORD, "$argon2id$stored-hash")).thenReturn(true);
        when(tenantTypePort.resolve("fan-platform")).thenReturn("B2C_CONSUMER");

        provider.authenticate(attempt(PASSWORD));

        verify(loginEventRecorder).recordSucceeded(eq("acc-1"), eq("fan-platform"), any());
    }

    @Test
    @DisplayName("BE-599 AC-2: unknown email → accountId=null, context tenant; the response is the SAME exception as a wrong password")
    void unknownEmail_failedWithNullAccountId_sameResponse() {
        bindRequest(null);
        when(credentialRepository.findAllByEmail(EMAIL)).thenReturn(List.of());

        assertThatThrownBy(() -> provider.authenticate(attempt(PASSWORD)))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid credentials");

        verify(loginEventRecorder).recordAttempted(isNull(), eq(EMAIL_HASH), eq("fan-platform"), any());
        verify(loginEventRecorder).recordFailed(
                isNull(), eq(EMAIL_HASH), eq("fan-platform"), eq("CREDENTIALS_INVALID"), any());
        verify(loginEventRecorder, never()).recordSucceeded(any(), any(), any());
    }

    @Test
    @DisplayName("BE-599: unknown email under a client tenant → the failed event carries the client tenant")
    void unknownEmail_withClientTenant_usesClientTenant() {
        bindRequest("ecommerce");
        when(credentialRepository.findByTenantIdAndEmail("ecommerce", EMAIL)).thenReturn(Optional.empty());
        when(credentialRepository.findAllByEmail(EMAIL)).thenReturn(List.of());

        assertThatThrownBy(() -> provider.authenticate(attempt(PASSWORD)))
                .isInstanceOf(BadCredentialsException.class);

        verify(loginEventRecorder).recordFailed(
                isNull(), eq(EMAIL_HASH), eq("ecommerce"), eq("CREDENTIALS_INVALID"), any());
    }

    @Test
    @DisplayName("BE-599: tenant ambiguity → LOGIN_TENANT_AMBIGUOUS with accountId=null, same response")
    void ambiguousEmail_failedAmbiguous() {
        bindRequest(null);
        when(credentialRepository.findAllByEmail(EMAIL))
                .thenReturn(List.of(credentialIn("fan-platform"), credentialIn("ecommerce")));

        assertThatThrownBy(() -> provider.authenticate(attempt(PASSWORD)))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid credentials");

        verify(loginEventRecorder).recordFailed(
                isNull(), eq(EMAIL_HASH), eq("fan-platform"), eq("LOGIN_TENANT_AMBIGUOUS"), any());
    }

    @Test
    @DisplayName("BE-599: a telemetry failure on success does NOT fail the login")
    void succeededTelemetryFailure_loginStillSucceeds() {
        bindRequest(null);
        stubHappyCredentialLookup();
        when(tenantTypePort.resolve("acme-corp")).thenReturn("B2B_ENTERPRISE");
        doThrow(new IllegalStateException("outbox down"))
                .when(loginEventRecorder).recordSucceeded(any(), any(), any());

        Authentication result = provider.authenticate(attempt(PASSWORD));

        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getName()).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("BE-599: a telemetry failure on a wrong password still yields BadCredentials, never the telemetry error")
    void failedTelemetryFailure_stillBadCredentials() {
        bindRequest(null);
        when(credentialRepository.findAllByEmail(EMAIL)).thenReturn(List.of(credential()));
        when(passwordHasher.verify("wrong", "$argon2id$stored-hash")).thenReturn(false);
        doThrow(new IllegalStateException("outbox down"))
                .when(loginEventRecorder).recordAttempted(any(), any(), any(), any());
        doThrow(new IllegalStateException("outbox down"))
                .when(loginEventRecorder).recordFailed(any(), any(), any(), any(), any());

        assertThatThrownBy(() -> provider.authenticate(attempt("wrong")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid credentials");
    }

    @Test
    @DisplayName("BE-599: blank credentials are a malformed submission — no login events")
    void blankPassword_noEvents() {
        assertThatThrownBy(() -> provider.authenticate(attempt(" ")))
                .isInstanceOf(BadCredentialsException.class);

        verifyNoInteractions(loginEventRecorder);
    }

    @Test
    @DisplayName("BE-599 AC-4: the provider reads only the servlet remote address — X-Forwarded-For is the container's job, not this code's")
    void clientIp_isRemoteAddr_notXForwardedFor() {
        MockHttpServletRequest request = bindRequest(null);
        // Behind Traefik the socket peer is the proxy; the real client is only in XFF.
        request.setRemoteAddr("172.18.0.5");
        request.addHeader("X-Forwarded-For", "198.51.100.9");
        stubHappyCredentialLookup();
        when(tenantTypePort.resolve("acme-corp")).thenReturn("B2B_ENTERPRISE");

        provider.authenticate(attempt(PASSWORD));

        ArgumentCaptor<SessionContext> ctx = ArgumentCaptor.forClass(SessionContext.class);
        verify(loginEventRecorder).recordSucceeded(any(), any(), ctx.capture());
        // Honouring XFF is container-level: server.forward-headers-strategy=FRAMEWORK (set by
        // the demo overlay infra/demo/iam-traefik.override.yml, NOT by application.yml)
        // installs Spring's ForwardedHeaderFilter, which rewrites getRemoteAddr() to the
        // X-Forwarded-For client before this code runs. A bare MockHttpServletRequest has no
        // such filter, so this proves only that the provider does no header parsing of its own
        // (and so cannot disagree with the filter).
        assertThat(ctx.getValue().ipMasked()).isEqualTo("172.18.*.*");
    }
}
