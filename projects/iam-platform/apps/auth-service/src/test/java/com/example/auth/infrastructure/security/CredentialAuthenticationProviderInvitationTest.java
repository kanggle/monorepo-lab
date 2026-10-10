package com.example.auth.infrastructure.security;

import com.example.auth.application.LoginEventRecorder;
import com.example.auth.application.port.AccountServicePort;
import com.example.auth.application.port.TenantTypePort;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-MONO-772 S3 (S1-7, auth-api.md § 운영자 초대 수락 «수락 화면에서 시작한 로그인은 풀 자격만 고른다») — a form
 * login whose continuation is the operator-invitation acceptance page (no initiating client) picks the
 * {@code consumer-pool} credential only.
 *
 * <p>Control: the same email with an {@code iam} AND a pool credential would be {@code LOGIN_TENANT_AMBIGUOUS} on
 * the client-less cross-tenant lookup (the row this test proves is NOT taken), and with only an {@code iam}
 * credential the acceptance login fails like a wrong password instead of producing a principal that cannot accept.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("CredentialAuthenticationProvider — 초대 수락 로그인은 풀 자격만 (TASK-MONO-772 S3 · S1-7)")
class CredentialAuthenticationProviderInvitationTest {

    private static final String EMAIL = "invitee@example.com";
    private static final String PASSWORD = "secret123";
    private static final String HASH = "$argon2id$stored-hash";
    private static final String POOL_ACCOUNT = "0199de70-0000-7000-8000-0000000c0772";

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
        Instant now = Instant.parse("2026-10-10T00:00:00Z");
        return new Credential(1L, accountId, tenantId, EMAIL, HASH, "argon2id", now, now, 0);
    }

    /** A form login with no initiating client, continuing (or not) to the acceptance page. */
    private void bindRequest(boolean acceptanceContinuation) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
        when(savedRequestTenantResolver.initiatingClientTenant(request, response)).thenReturn(Optional.empty());
        when(savedRequestTenantResolver.continuesToOperatorInvitationAcceptance(request, response))
                .thenReturn(acceptanceContinuation);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> details(Authentication a) {
        return (Map<String, Object>) a.getDetails();
    }

    @Test
    @DisplayName("🔴 수락 화면 계속 · 같은 이메일에 iam 자격도 있음 → 풀 자격으로 로그인(교차 조회 안 함) · principal=consumer-pool")
    void acceptanceLogin_picksThePoolCredential() {
        bindRequest(true);
        when(credentialRepository.findPoolCredentialByEmail(EMAIL))
                .thenReturn(Optional.of(credential(POOL_ACCOUNT, "consumer-pool")));
        when(passwordHasher.verify(PASSWORD, HASH)).thenReturn(true);
        when(tenantTypePort.resolve("consumer-pool")).thenReturn("B2C_CONSUMER");

        Authentication result = provider.authenticate(new UsernamePasswordAuthenticationToken(EMAIL, PASSWORD));

        assertThat(details(result)).containsEntry("tenant_id", "consumer-pool").containsEntry("account_id", POOL_ACCOUNT);
        verify(credentialRepository, never()).findAllByEmail(anyString());
        verify(credentialRepository, never()).findByTenantIdAndEmail(anyString(), anyString());
    }

    @Test
    @DisplayName("🔴 수락 화면 계속 · 풀 자격 없음(iam 자격만) → 오답 비밀번호와 같은 BadCredentials · iam 자격을 고르지 않는다")
    void acceptanceLogin_noPoolCredential_refused() {
        bindRequest(true);
        when(credentialRepository.findPoolCredentialByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> provider.authenticate(new UsernamePasswordAuthenticationToken(EMAIL, PASSWORD)))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid credentials");
        verify(credentialRepository, never()).findAllByEmail(anyString());
        verify(passwordHasher, never()).verify(anyString(), anyString());
    }

    @Test
    @DisplayName("대조군: 수락 화면이 아닌 client 없는 로그인 → 지금 그대로 교차 조회(iam + 풀 = 모호 → 거절)")
    void plainClientlessLogin_unchanged() {
        bindRequest(false);
        when(credentialRepository.findAllByEmail(EMAIL)).thenReturn(List.of(
                credential("iam-acc", "iam"), credential(POOL_ACCOUNT, "consumer-pool")));

        assertThatThrownBy(() -> provider.authenticate(new UsernamePasswordAuthenticationToken(EMAIL, PASSWORD)))
                .isInstanceOf(BadCredentialsException.class);
        verify(credentialRepository, never()).findPoolCredentialByEmail(anyString());
    }
}
