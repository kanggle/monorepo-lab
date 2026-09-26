package com.example.auth.infrastructure.security;

import com.example.auth.application.LoginEventRecorder;
import com.example.auth.application.LoginHashes;
import com.example.auth.application.exception.AccountServiceUnavailableException;
import com.example.auth.domain.credentials.Credential;
import com.example.auth.application.port.AccountServicePort;
import com.example.auth.application.port.TenantTypePort;
import com.example.auth.application.result.AccountStatusLookupResult;
import com.example.auth.domain.repository.CredentialRepository;
import com.example.auth.domain.session.SessionContext;
import com.example.security.password.PasswordHasher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
import static org.mockito.Mockito.inOrder;
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

    // TASK-BE-600: unstubbed → Optional.empty() (= 404, "no account record"), so the BE-599
    // tests above keep exercising the path they always did.
    @Mock
    private AccountServicePort accountServicePort;

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
        // TASK-BE-604: the provider asks for the INITIATING client's tenant only — resolve()
        // would substitute fan-platform for "no client" and hide the difference.
        when(savedRequestTenantResolver.initiatingClientTenant(request, response))
                .thenReturn(Optional.ofNullable(clientTenant));
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
    @DisplayName("BE-599 AC-2: cross-tenant fallback (console client) → the failed event carries the ACCOUNT's tenant, not the initiating client's")
    void wrongPassword_crossTenantFallback_usesAccountTenant() {
        // TASK-BE-604: the fallback exists only for the console client now.
        bindRequest("iam");
        when(credentialRepository.findByTenantIdAndEmail("iam", EMAIL)).thenReturn(Optional.empty());
        when(credentialRepository.findAllByEmail(EMAIL)).thenReturn(List.of(credentialIn("fan-platform")));
        when(passwordHasher.verify("wrong", "$argon2id$stored-hash")).thenReturn(false);

        assertThatThrownBy(() -> provider.authenticate(attempt("wrong")))
                .isInstanceOf(BadCredentialsException.class);

        verify(loginEventRecorder).recordAttempted(eq("acc-1"), eq(EMAIL_HASH), eq("fan-platform"), any());
        verify(loginEventRecorder).recordFailed(
                eq("acc-1"), eq(EMAIL_HASH), eq("fan-platform"), eq("CREDENTIALS_INVALID"), any());
    }

    @Test
    @DisplayName("BE-599 / BE-604: success through the console cross-tenant fallback → the principal and succeeded carry the account's tenant")
    void success_crossTenantFallback_usesAccountTenant() {
        bindRequest("iam");
        when(credentialRepository.findByTenantIdAndEmail("iam", EMAIL)).thenReturn(Optional.empty());
        when(credentialRepository.findAllByEmail(EMAIL)).thenReturn(List.of(credentialIn("fan-platform")));
        when(passwordHasher.verify(PASSWORD, "$argon2id$stored-hash")).thenReturn(true);
        when(tenantTypePort.resolve("fan-platform")).thenReturn("B2C_CONSUMER");

        Authentication result = provider.authenticate(attempt(PASSWORD));

        verify(loginEventRecorder).recordSucceeded(eq("acc-1"), eq("fan-platform"), any());
        // The login-time tenant the SAS session carries — and that the refresh tenant check
        // compares the mirror row with (TASK-BE-604 AuthorizationSessionTenant).
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) result.getDetails();
        assertThat(details).containsEntry("tenant_id", "fan-platform");
    }

    // ---------------------------------------------------------------------------------
    // TASK-BE-604 — which accounts may log into which clients (owner decision D)
    // ---------------------------------------------------------------------------------

    @Test
    @DisplayName("BE-604 D: consumer client, scoped miss, account exists in another tenant → refused like a wrong password; "
            + "no cross-tenant lookup, no password check")
    void consumerClient_scopedMiss_noCrossTenantFallback() {
        bindRequest("ecommerce");
        when(credentialRepository.findByTenantIdAndEmail("ecommerce", EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> provider.authenticate(attempt(PASSWORD)))
                .isExactlyInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid credentials");

        // 🔴 The load-bearing assertion: the fan-platform credential is never even looked at.
        verify(credentialRepository, never()).findAllByEmail(any());
        verifyNoInteractions(passwordHasher, accountServicePort, tenantTypePort);
        // Same telemetry as an unknown email under that client: no identity, the client's tenant.
        verify(loginEventRecorder).recordAttempted(isNull(), eq(EMAIL_HASH), eq("ecommerce"), any());
        verify(loginEventRecorder).recordFailed(
                isNull(), eq(EMAIL_HASH), eq("ecommerce"), eq("CREDENTIALS_INVALID"), any());
        verify(loginEventRecorder, never()).recordSucceeded(any(), any(), any());
    }

    @Test
    @DisplayName("BE-604 D (control): consumer client, scoped HIT → logs in, principal tenant = the client's tenant")
    void consumerClient_scopedHit_succeeds() {
        bindRequest("ecommerce");
        when(credentialRepository.findByTenantIdAndEmail("ecommerce", EMAIL))
                .thenReturn(Optional.of(credentialIn("ecommerce")));
        when(passwordHasher.verify(PASSWORD, "$argon2id$stored-hash")).thenReturn(true);
        when(tenantTypePort.resolve("ecommerce")).thenReturn("B2C_CONSUMER");

        Authentication result = provider.authenticate(attempt(PASSWORD));

        assertThat(result.isAuthenticated()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) result.getDetails();
        assertThat(details).containsEntry("tenant_id", "ecommerce");
        verify(credentialRepository, never()).findAllByEmail(any());
    }

    @Test
    @DisplayName("BE-604 D: console client, scoped HIT (an iam credential) → no cross-tenant lookup")
    void consoleClient_scopedHit_noFallback() {
        bindRequest("iam");
        when(credentialRepository.findByTenantIdAndEmail("iam", EMAIL))
                .thenReturn(Optional.of(credentialIn("iam")));
        when(passwordHasher.verify(PASSWORD, "$argon2id$stored-hash")).thenReturn(true);
        when(tenantTypePort.resolve("iam")).thenReturn("B2B_ENTERPRISE");

        provider.authenticate(attempt(PASSWORD));

        verify(credentialRepository, never()).findAllByEmail(any());
        verify(loginEventRecorder).recordSucceeded(eq("acc-1"), eq("iam"), any());
    }

    @Test
    @DisplayName("BE-604 D: console client, scoped miss, email in TWO consumer tenants → fail-closed LOGIN_TENANT_AMBIGUOUS")
    void consoleClient_scopedMiss_ambiguous_failsClosed() {
        bindRequest("iam");
        when(credentialRepository.findByTenantIdAndEmail("iam", EMAIL)).thenReturn(Optional.empty());
        when(credentialRepository.findAllByEmail(EMAIL))
                .thenReturn(List.of(credentialIn("fan-platform"), credentialIn("ecommerce")));

        assertThatThrownBy(() -> provider.authenticate(attempt(PASSWORD)))
                .isExactlyInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid credentials");

        verifyNoInteractions(passwordHasher);
        verify(loginEventRecorder).recordFailed(
                isNull(), eq(EMAIL_HASH), eq("iam"), eq("LOGIN_TENANT_AMBIGUOUS"), any());
    }

    @Test
    @DisplayName("BE-604: no initiating client (direct /login) → the cross-tenant lookup runs WITHOUT a fan-platform-scoped lookup first")
    void noInitiatingClient_crossTenantLookupOnly() {
        bindRequest(null);
        when(credentialRepository.findAllByEmail(EMAIL)).thenReturn(List.of(credentialIn("ecommerce")));
        when(passwordHasher.verify(PASSWORD, "$argon2id$stored-hash")).thenReturn(true);
        when(tenantTypePort.resolve("ecommerce")).thenReturn("B2C_CONSUMER");

        provider.authenticate(attempt(PASSWORD));

        verify(credentialRepository, never()).findByTenantIdAndEmail(any(), any());
        verify(loginEventRecorder).recordSucceeded(eq("acc-1"), eq("ecommerce"), any());
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
        // TASK-BE-604: a consumer client's scoped miss ends the lookup — no findAllByEmail.

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

    // ---------------------------------------------------------------------------------
    // TASK-BE-600 — account status on the form-login path
    // ---------------------------------------------------------------------------------

    private void stubStatus(String status) {
        when(accountServicePort.getAccountStatus("acc-1", "acme-corp"))
                .thenReturn(Optional.of(new AccountStatusLookupResult("acc-1", status)));
    }

    /** The wrong-password outcome, captured live so the status rejections are compared to it, not to a literal. */
    private Throwable wrongPasswordOutcome() {
        when(credentialRepository.findAllByEmail(EMAIL)).thenReturn(List.of(credential()));
        when(passwordHasher.verify("wrong", "$argon2id$stored-hash")).thenReturn(false);
        Throwable t = org.assertj.core.api.Assertions.catchThrowable(
                () -> provider.authenticate(attempt("wrong")));
        assertThat(t).isNotNull();
        return t;
    }

    /**
     * Right password, non-ACTIVE status → the SAME outcome as a wrong password (AC-1 ⓐ):
     * same exception type, same message — so /login?error and its text are identical and
     * a locked account cannot be used to confirm its password.
     */
    private void assertRejectedLikeWrongPassword(String status, String expectedReason) {
        bindRequest(null);
        Throwable wrongPassword = wrongPasswordOutcome();

        stubHappyCredentialLookup();
        stubStatus(status);

        Throwable rejected = org.assertj.core.api.Assertions.catchThrowable(
                () -> provider.authenticate(attempt(PASSWORD)));

        assertThat(rejected).isNotNull();
        assertThat(rejected.getClass()).isEqualTo(wrongPassword.getClass());
        assertThat(rejected).isExactlyInstanceOf(BadCredentialsException.class);
        assertThat(rejected.getMessage()).isEqualTo(wrongPassword.getMessage());
        assertThat(rejected.getCause()).isNull();

        // Telemetry: a failed attempt against a resolved identity, with the contract reason.
        verify(loginEventRecorder).recordFailed(
                eq("acc-1"), eq(EMAIL_HASH), eq("acme-corp"), eq(expectedReason), any());
        verify(loginEventRecorder, never()).recordSucceeded(any(), any(), any());
        // Nothing past the status gate ran.
        verifyNoInteractions(tenantTypePort);
    }

    @Test
    @DisplayName("BE-600 AC-2: ACTIVE → login succeeds; status is looked up in the ACCOUNT's tenant")
    void activeAccount_succeeds() {
        bindRequest(null);
        stubHappyCredentialLookup();
        stubStatus("ACTIVE");
        when(tenantTypePort.resolve("acme-corp")).thenReturn("B2B_ENTERPRISE");

        Authentication result = provider.authenticate(attempt(PASSWORD));

        assertThat(result.isAuthenticated()).isTrue();
        verify(accountServicePort).getAccountStatus("acc-1", "acme-corp");
        verify(loginEventRecorder).recordSucceeded(eq("acc-1"), eq("acme-corp"), any());
    }

    @Test
    @DisplayName("BE-600 AC-1/AC-2: LOCKED + right password → identical to a wrong password; failed=ACCOUNT_LOCKED")
    void lockedAccount_rejectedLikeWrongPassword() {
        assertRejectedLikeWrongPassword("LOCKED", "ACCOUNT_LOCKED");
    }

    @Test
    @DisplayName("BE-600 AC-1/AC-2: DORMANT + right password → identical to a wrong password; failed=ACCOUNT_DORMANT")
    void dormantAccount_rejectedLikeWrongPassword() {
        assertRejectedLikeWrongPassword("DORMANT", "ACCOUNT_DORMANT");
    }

    @Test
    @DisplayName("BE-600 AC-1/AC-2: DELETED + right password → identical to a wrong password; failed=ACCOUNT_DELETED")
    void deletedAccount_rejectedLikeWrongPassword() {
        assertRejectedLikeWrongPassword("DELETED", "ACCOUNT_DELETED");
    }

    @Test
    @DisplayName("BE-600 AC-1: status is fetched BEFORE and the password is verified REGARDLESS — "
            + "a LOCKED account with a wrong password is a plain CREDENTIALS_INVALID")
    void lockedAccount_wrongPassword_stillVerifiesPassword_credentialsInvalid() {
        bindRequest(null);
        when(credentialRepository.findAllByEmail(EMAIL)).thenReturn(List.of(credential()));
        stubStatus("LOCKED");
        when(passwordHasher.verify("wrong", "$argon2id$stored-hash")).thenReturn(false);

        assertThatThrownBy(() -> provider.authenticate(attempt("wrong")))
                .isExactlyInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid credentials");

        // Same two costs as any other found credential, in this order: no timing split
        // between locked and active, nor between right and wrong password on a locked account.
        InOrder order = inOrder(accountServicePort, passwordHasher);
        order.verify(accountServicePort).getAccountStatus("acc-1", "acme-corp");
        order.verify(passwordHasher).verify("wrong", "$argon2id$stored-hash");
        verify(loginEventRecorder).recordFailed(
                eq("acc-1"), eq(EMAIL_HASH), eq("acme-corp"), eq("CREDENTIALS_INVALID"), any());
        verify(loginEventRecorder, never()).recordFailed(any(), any(), any(), eq("ACCOUNT_LOCKED"), any());
    }

    @Test
    @DisplayName("BE-600: a status outside the contract enum is rejected like a wrong password, with no invented failureReason")
    void unknownStatus_rejected_noFailedEvent() {
        bindRequest(null);
        stubHappyCredentialLookup();
        stubStatus("SUSPENDED");

        assertThatThrownBy(() -> provider.authenticate(attempt(PASSWORD)))
                .isExactlyInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid credentials");

        verify(loginEventRecorder).recordAttempted(eq("acc-1"), eq(EMAIL_HASH), eq("acme-corp"), any());
        verify(loginEventRecorder, never()).recordFailed(any(), any(), any(), any(), any());
        verify(loginEventRecorder, never()).recordSucceeded(any(), any(), any());
    }

    @Test
    @DisplayName("BE-600: 404 (no account record — e.g. a console operator in tenant iam) → the rule has nothing to apply; login succeeds")
    void noAccountRecord_404_proceeds() {
        bindRequest(null);
        when(credentialRepository.findAllByEmail(EMAIL)).thenReturn(List.of(credentialIn("iam")));
        when(passwordHasher.verify(PASSWORD, "$argon2id$stored-hash")).thenReturn(true);
        when(accountServicePort.getAccountStatus("acc-1", "iam")).thenReturn(Optional.empty());
        when(tenantTypePort.resolve("iam")).thenReturn("B2B_ENTERPRISE");

        Authentication result = provider.authenticate(attempt(PASSWORD));

        assertThat(result.isAuthenticated()).isTrue();
    }

    @Test
    @DisplayName("BE-600 AC-2: status lookup FAILURE → fail-closed (AuthenticationServiceException → /login?error); "
            + "no success, no invented failureReason")
    void statusLookupFailure_failsClosed() {
        bindRequest(null);
        when(credentialRepository.findAllByEmail(EMAIL)).thenReturn(List.of(credential()));
        when(accountServicePort.getAccountStatus("acc-1", "acme-corp"))
                .thenThrow(new AccountServiceUnavailableException("down"));

        assertThatThrownBy(() -> provider.authenticate(attempt(PASSWORD)))
                .isInstanceOf(AuthenticationServiceException.class)
                .hasCauseInstanceOf(AccountServiceUnavailableException.class);

        verify(loginEventRecorder, never()).recordSucceeded(any(), any(), any());
        verify(loginEventRecorder, never()).recordFailed(any(), any(), any(), any(), any());
        verifyNoInteractions(tenantTypePort);
    }

    @Test
    @DisplayName("BE-600: unknown email → no status lookup at all (no identity to look up)")
    void unknownEmail_noStatusLookup() {
        bindRequest(null);
        when(credentialRepository.findAllByEmail(EMAIL)).thenReturn(List.of());

        assertThatThrownBy(() -> provider.authenticate(attempt(PASSWORD)))
                .isExactlyInstanceOf(BadCredentialsException.class);

        verifyNoInteractions(accountServicePort);
    }
}
