package com.example.auth.infrastructure.security;

import com.example.auth.application.LoginEventRecorder;
import com.example.auth.application.exception.AccountServiceUnavailableException;
import com.example.auth.application.port.AccountServicePort;
import com.example.auth.application.port.TenantTypePort;
import com.example.auth.application.result.ConsumerSiteMembershipLookupResult;
import com.example.auth.domain.credentials.Credential;
import com.example.auth.domain.repository.CredentialRepository;
import com.example.security.password.PasswordHasher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-615 — the form login's consumer-pool row (multi-tenancy.md § 소비자 계정 풀 § 4: «소비자
 * client 는 풀 자격을 먼저, 없으면 그 client 테넌트의 사이트별 자격»), and the console's cross-tenant
 * lookup with a pool credential (AC-3, contract § 4 last bullet).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("CredentialAuthenticationProvider — 풀 자격 (TASK-BE-615)")
class CredentialAuthenticationProviderPoolTest {

    private static final String EMAIL = "pool@example.com";
    private static final String PASSWORD = "secret123";
    private static final String HASH = "$argon2id$stored-hash";
    private static final String POOL_ACCOUNT = "0199de70-0000-7000-8000-0000000c0615";

    @Mock private CredentialRepository credentialRepository;
    @Mock private PasswordHasher passwordHasher;
    @Mock private TenantTypePort tenantTypePort;
    @Mock private SavedRequestTenantResolver savedRequestTenantResolver;
    @Mock private LoginEventRecorder loginEventRecorder;
    @Mock private AccountServicePort accountServicePort;

    @InjectMocks private CredentialAuthenticationProvider provider;

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static Credential credential(String accountId, String tenantId) {
        Instant now = Instant.parse("2026-10-01T00:00:00Z");
        return new Credential(1L, accountId, tenantId, EMAIL, HASH, "argon2id", now, now, 0);
    }

    private void bindRequest(String clientTenant) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
        when(savedRequestTenantResolver.initiatingClientTenant(request, response))
                .thenReturn(Optional.ofNullable(clientTenant));
    }

    private void consumerSite(String site, boolean isConsumerSite) {
        when(accountServicePort.getConsumerSiteMembership(site, POOL_ACCOUNT)).thenReturn(
                new ConsumerSiteMembershipLookupResult(site, isConsumerSite,
                        isConsumerSite ? "B2C_CONSUMER" : "B2B_ENTERPRISE", null, List.of()));
    }

    private void passwordOk() {
        when(passwordHasher.verify(PASSWORD, HASH)).thenReturn(true);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> details(Authentication a) {
        return (Map<String, Object>) a.getDetails();
    }

    @Test
    @DisplayName("소비자 client(스토어) · 풀 자격 있음 → 풀 자격으로 로그인 (사이트 범위 조회 없음) · principal tenant=consumer-pool")
    void consumerClient_poolCredential_wins() {
        bindRequest("ecommerce");
        when(credentialRepository.findPoolCredentialByEmail(EMAIL))
                .thenReturn(Optional.of(credential(POOL_ACCOUNT, "consumer-pool")));
        consumerSite("ecommerce", true);
        passwordOk();
        when(tenantTypePort.resolve("consumer-pool")).thenReturn("B2C_CONSUMER");

        Authentication result = provider.authenticate(new UsernamePasswordAuthenticationToken(EMAIL, PASSWORD));

        assertThat(details(result)).containsEntry("tenant_id", "consumer-pool")
                .containsEntry("account_id", POOL_ACCOUNT);
        verify(credentialRepository, never()).findByTenantIdAndEmail(anyString(), anyString());
        // The status is looked up where the account lives — the pool.
        verify(accountServicePort).getAccountStatus(POOL_ACCOUNT, "consumer-pool");
    }

    @Test
    @DisplayName("AC-9 대조군: 소비자 client · 풀 자격 없음 → 지금 그대로 사이트 범위 조회 · 사이트 조회 0")
    void consumerClient_noPoolCredential_unchanged() {
        bindRequest("ecommerce");
        when(credentialRepository.findPoolCredentialByEmail(EMAIL)).thenReturn(Optional.empty());
        when(credentialRepository.findByTenantIdAndEmail("ecommerce", EMAIL))
                .thenReturn(Optional.of(credential("acc-site", "ecommerce")));
        passwordOk();
        when(tenantTypePort.resolve("ecommerce")).thenReturn("B2C_CONSUMER");

        Authentication result = provider.authenticate(new UsernamePasswordAuthenticationToken(EMAIL, PASSWORD));

        assertThat(details(result)).containsEntry("tenant_id", "ecommerce");
        verify(accountServicePort, never()).getConsumerSiteMembership(anyString(), anyString());
    }

    @Test
    @DisplayName("B2B client(wms) · 같은 이메일 풀 자격 있음 → 풀 무시 · wms 자격으로 로그인 (풀-먼저는 소비자 사이트 규칙)")
    void b2bClient_poolCredentialIgnored() {
        bindRequest("wms");
        when(credentialRepository.findPoolCredentialByEmail(EMAIL))
                .thenReturn(Optional.of(credential(POOL_ACCOUNT, "consumer-pool")));
        consumerSite("wms", false);
        when(credentialRepository.findByTenantIdAndEmail("wms", EMAIL))
                .thenReturn(Optional.of(credential("acc-wms", "wms")));
        passwordOk();
        when(tenantTypePort.resolve("wms")).thenReturn("B2B_ENTERPRISE");

        Authentication result = provider.authenticate(new UsernamePasswordAuthenticationToken(EMAIL, PASSWORD));

        assertThat(details(result)).containsEntry("tenant_id", "wms").containsEntry("account_id", "acc-wms");
    }

    @Test
    @DisplayName("풀 자격이 있는데 소비자 사이트 조회 실패 → fail-closed (AuthenticationServiceException) — 어느 자격도 고르지 않는다")
    void consumerSiteLookupFailure_failsClosed() {
        bindRequest("ecommerce");
        when(credentialRepository.findPoolCredentialByEmail(EMAIL))
                .thenReturn(Optional.of(credential(POOL_ACCOUNT, "consumer-pool")));
        when(accountServicePort.getConsumerSiteMembership("ecommerce", POOL_ACCOUNT))
                .thenThrow(new AccountServiceUnavailableException("down"));

        assertThatThrownBy(() -> provider.authenticate(new UsernamePasswordAuthenticationToken(EMAIL, PASSWORD)))
                .isInstanceOf(AuthenticationServiceException.class);
        verify(credentialRepository, never()).findByTenantIdAndEmail(anyString(), anyString());
        verify(passwordHasher, never()).verify(any(), any());
    }

    @Test
    @DisplayName("풀 자격 · 틀린 비밀번호 → 틀린 비밀번호와 같은 응답 (사이트 자격으로 넘어가지 않는다)")
    void poolCredential_wrongPassword_badCredentials() {
        bindRequest("fan-platform");
        when(credentialRepository.findPoolCredentialByEmail(EMAIL))
                .thenReturn(Optional.of(credential(POOL_ACCOUNT, "consumer-pool")));
        consumerSite("fan-platform", true);
        when(passwordHasher.verify("wrong", HASH)).thenReturn(false);

        assertThatThrownBy(() -> provider.authenticate(new UsernamePasswordAuthenticationToken(EMAIL, "wrong")))
                .isExactlyInstanceOf(BadCredentialsException.class).hasMessage("Invalid credentials");
        verify(credentialRepository, never()).findByTenantIdAndEmail(anyString(), anyString());
        verify(loginEventRecorder).recordFailed(eq(POOL_ACCOUNT), any(), eq("consumer-pool"),
                eq("CREDENTIALS_INVALID"), any());
    }

    // ── AC-3 — the console's cross-tenant lookup with a pool credential ──────────────────────

    @Test
    @DisplayName("AC-3: 콘솔 client · iam 자격 없음 · 이메일이 풀 한 행뿐 → 교차 조회가 그 풀 자격 하나로 풀린다(LOGIN_TENANT_AMBIGUOUS 아님) · 풀-먼저는 콘솔에서 안 탄다")
    void console_poolOnlyEmail_resolvesToThePoolCredential() {
        bindRequest("iam");
        when(credentialRepository.findByTenantIdAndEmail("iam", EMAIL)).thenReturn(Optional.empty());
        when(credentialRepository.findAllByEmail(EMAIL)).thenReturn(List.of(credential(POOL_ACCOUNT, "consumer-pool")));
        passwordOk();
        when(tenantTypePort.resolve("consumer-pool")).thenReturn("B2C_CONSUMER");

        Authentication result = provider.authenticate(new UsernamePasswordAuthenticationToken(EMAIL, PASSWORD));

        // A session — but its console token is refused (the session tenant stays consumer-pool on the
        // console; TenantClaimPoolPrincipalTest#consoleClient_poolPrincipal_refusedByIssuerGate).
        assertThat(details(result)).containsEntry("tenant_id", "consumer-pool");
        verify(credentialRepository, never()).findPoolCredentialByEmail(anyString());
        verify(accountServicePort, never()).getConsumerSiteMembership(anyString(), anyString());
    }

    @Test
    @DisplayName("AC-3 대조군: 묶기 전 — 같은 이메일이 팬·스토어 사이트에 둘 다 → 지금처럼 LOGIN_TENANT_AMBIGUOUS")
    void console_twoSiteAccounts_stillAmbiguous() {
        bindRequest("iam");
        when(credentialRepository.findByTenantIdAndEmail("iam", EMAIL)).thenReturn(Optional.empty());
        when(credentialRepository.findAllByEmail(EMAIL))
                .thenReturn(List.of(credential("acc-fan", "fan-platform"), credential("acc-store", "ecommerce")));

        assertThatThrownBy(() -> provider.authenticate(new UsernamePasswordAuthenticationToken(EMAIL, PASSWORD)))
                .isExactlyInstanceOf(BadCredentialsException.class);
        verify(loginEventRecorder).recordFailed(any(), any(), eq("iam"), eq("LOGIN_TENANT_AMBIGUOUS"), any());
    }
}
