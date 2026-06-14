package com.example.auth.infrastructure.oauth2;

import com.example.auth.application.exception.AssumeTenantDeniedException;
import com.example.auth.application.port.OperatorAssignmentPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContext;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-327 (ADR-MONO-020 D2) — unit tests for
 * {@link AssumeTenantAuthenticationProvider}, the assume-tenant exchange's core:
 * <ul>
 *   <li>assigned → mints a short-lived access token, NO refresh token, same iss/kid path</li>
 *   <li>subject token invalid → {@code invalid_grant} (no mint, no admin call)</li>
 *   <li><b>assignment-denied → no token</b> ({@code invalid_grant})</li>
 *   <li><b>admin-unavailable → fail-closed deny</b> ({@code invalid_grant}; the port throws
 *       {@link AssumeTenantDeniedException})</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("AssumeTenantAuthenticationProvider 단위 테스트 (TASK-BE-327, fail-closed gate)")
class AssumeTenantAuthenticationProviderTest {

    private static final String SUBJECT_TOKEN = "base-iam-oidc-token";
    private static final String OIDC_SUBJECT = "00000000-0000-7000-8000-0000000000a1";
    private static final String SELECTED_TENANT = "acme-corp";

    @Mock
    private JwtDecoder subjectTokenDecoder;
    @Mock
    private OperatorAssignmentPort operatorAssignmentPort;
    @Mock
    private OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator;

    private AssumeTenantAuthenticationProvider provider;

    @BeforeEach
    void setUp() {
        provider = new AssumeTenantAuthenticationProvider(
                subjectTokenDecoder, operatorAssignmentPort, tokenGenerator);

        AuthorizationServerContext ctx = new AuthorizationServerContext() {
            @Override
            public String getIssuer() { return "http://localhost:8081"; }
            @Override
            public AuthorizationServerSettings getAuthorizationServerSettings() {
                return AuthorizationServerSettings.builder().issuer("http://localhost:8081").build();
            }
        };
        AuthorizationServerContextHolder.setContext(ctx);
    }

    @AfterEach
    void tearDown() {
        AuthorizationServerContextHolder.resetContext();
    }

    private AssumeTenantAuthenticationToken exchange() {
        RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("platform-console-web")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(new org.springframework.security.oauth2.core.AuthorizationGrantType(
                        "urn:ietf:params:oauth:grant-type:token-exchange"))
                .build();
        Authentication clientPrincipal = new OAuth2ClientAuthenticationToken(
                client, ClientAuthenticationMethod.NONE, null);
        return new AssumeTenantAuthenticationToken(
                clientPrincipal, SUBJECT_TOKEN,
                "urn:ietf:params:oauth:token-type:access_token", SELECTED_TENANT);
    }

    private Jwt validSubjectJwt() {
        return Jwt.withTokenValue(SUBJECT_TOKEN)
                .header("alg", "RS256")
                .subject(OIDC_SUBJECT)
                .claim("tenant_id", "iam")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }

    @Test
    @DisplayName("assigned → mint short-lived access token, NO refresh token")
    void assigned_mintsAccessTokenNoRefresh() {
        when(subjectTokenDecoder.decode(SUBJECT_TOKEN)).thenReturn(validSubjectJwt());
        when(operatorAssignmentPort.resolveAssignment(OIDC_SUBJECT, SELECTED_TENANT))
                .thenReturn(new OperatorAssignmentPort.AssignmentResult(true, null));
        Jwt minted = Jwt.withTokenValue("assumed-token")
                .header("alg", "RS256")
                .subject(OIDC_SUBJECT)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(1800))
                .build();
        doReturn(minted).when(tokenGenerator).generate(any());

        Authentication result = provider.authenticate(exchange());

        assertThat(result).isInstanceOf(OAuth2AccessTokenAuthenticationToken.class);
        OAuth2AccessTokenAuthenticationToken token = (OAuth2AccessTokenAuthenticationToken) result;
        assertThat(token.getAccessToken().getTokenValue()).isEqualTo("assumed-token");
        // NO refresh token for the assumed token (short-lived, re-minted per selection).
        assertThat(token.getRefreshToken()).isNull();
    }

    @Test
    @DisplayName("TASK-BE-329: operator's account_type from subject token carried onto the resolved grant")
    void preservesOperatorAccountType_onResolvedGrant() {
        Jwt operatorSubject = Jwt.withTokenValue(SUBJECT_TOKEN)
                .header("alg", "RS256")
                .subject(OIDC_SUBJECT)
                .claim("tenant_id", "acme-corp")
                .claim("account_type", "OPERATOR")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        when(subjectTokenDecoder.decode(SUBJECT_TOKEN)).thenReturn(operatorSubject);
        when(operatorAssignmentPort.resolveAssignment(OIDC_SUBJECT, SELECTED_TENANT))
                .thenReturn(new OperatorAssignmentPort.AssignmentResult(true, null));
        Jwt minted = Jwt.withTokenValue("assumed-token")
                .header("alg", "RS256").subject(OIDC_SUBJECT)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(1800)).build();
        doReturn(minted).when(tokenGenerator).generate(any());

        provider.authenticate(exchange());

        org.mockito.ArgumentCaptor<org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext> captor =
                org.mockito.ArgumentCaptor.forClass(
                        org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext.class);
        verify(tokenGenerator).generate(captor.capture());
        Object grant = captor.getValue().getAuthorizationGrant();
        assertThat(grant).isInstanceOf(AssumeTenantAuthenticationToken.class);
        // The operator stays OPERATOR while acting for the customer (ADR-MONO-021 D3).
        assertThat(((AssumeTenantAuthenticationToken) grant).getOperatorAccountType()).isEqualTo("OPERATOR");
    }

    @Test
    @DisplayName("TASK-BE-338: resolved org_scope carried onto the resolved grant")
    void carriesResolvedOrgScope_onResolvedGrant() {
        when(subjectTokenDecoder.decode(SUBJECT_TOKEN)).thenReturn(validSubjectJwt());
        when(operatorAssignmentPort.resolveAssignment(OIDC_SUBJECT, SELECTED_TENANT))
                .thenReturn(new OperatorAssignmentPort.AssignmentResult(
                        true, java.util.List.of("dept-sales")));
        Jwt minted = Jwt.withTokenValue("assumed-token")
                .header("alg", "RS256").subject(OIDC_SUBJECT)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(1800)).build();
        doReturn(minted).when(tokenGenerator).generate(any());

        provider.authenticate(exchange());

        org.mockito.ArgumentCaptor<org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext> captor =
                org.mockito.ArgumentCaptor.forClass(
                        org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext.class);
        verify(tokenGenerator).generate(captor.capture());
        Object grant = captor.getValue().getAuthorizationGrant();
        assertThat(grant).isInstanceOf(AssumeTenantAuthenticationToken.class);
        assertThat(((AssumeTenantAuthenticationToken) grant).getOrgScope())
                .containsExactly("dept-sales");
    }

    @Test
    @DisplayName("TASK-BE-338 net-zero: null org_scope carried as null (customizer → [*])")
    void carriesNullOrgScope_netZero() {
        when(subjectTokenDecoder.decode(SUBJECT_TOKEN)).thenReturn(validSubjectJwt());
        when(operatorAssignmentPort.resolveAssignment(OIDC_SUBJECT, SELECTED_TENANT))
                .thenReturn(new OperatorAssignmentPort.AssignmentResult(true, null));
        Jwt minted = Jwt.withTokenValue("assumed-token")
                .header("alg", "RS256").subject(OIDC_SUBJECT)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(1800)).build();
        doReturn(minted).when(tokenGenerator).generate(any());

        provider.authenticate(exchange());

        org.mockito.ArgumentCaptor<org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext> captor =
                org.mockito.ArgumentCaptor.forClass(
                        org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext.class);
        verify(tokenGenerator).generate(captor.capture());
        AssumeTenantAuthenticationToken grant =
                (AssumeTenantAuthenticationToken) captor.getValue().getAuthorizationGrant();
        assertThat(grant.getOrgScope()).isNull();
    }

    @Test
    @DisplayName("TASK-BE-376: subject token roles NOT extracted/threaded — roles derived at customizer from entitled domains")
    void doesNotThreadSubjectRoles_onResolvedGrant() {
        // The subject token may carry `roles` from a prior leg, but TASK-BE-376 no
        // longer preserves them — the resolved grant carries only account_type +
        // org_scope; the customizer DERIVES roles from the selected tenant's
        // entitled domains. The grant's account_type/org_scope plumbing is intact.
        Jwt operatorSubject = Jwt.withTokenValue(SUBJECT_TOKEN)
                .header("alg", "RS256")
                .subject(OIDC_SUBJECT)
                .claim("tenant_id", "iam")
                .claim("account_type", "OPERATOR")
                .claim("roles", java.util.List.of("STALE_BASE_ROLE"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        when(subjectTokenDecoder.decode(SUBJECT_TOKEN)).thenReturn(operatorSubject);
        when(operatorAssignmentPort.resolveAssignment(OIDC_SUBJECT, SELECTED_TENANT))
                .thenReturn(new OperatorAssignmentPort.AssignmentResult(
                        true, java.util.List.of("dept-sales")));
        Jwt minted = Jwt.withTokenValue("assumed-token")
                .header("alg", "RS256").subject(OIDC_SUBJECT)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(1800)).build();
        doReturn(minted).when(tokenGenerator).generate(any());

        provider.authenticate(exchange());

        org.mockito.ArgumentCaptor<org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext> captor =
                org.mockito.ArgumentCaptor.forClass(
                        org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext.class);
        verify(tokenGenerator).generate(captor.capture());
        Object grant = captor.getValue().getAuthorizationGrant();
        assertThat(grant).isInstanceOf(AssumeTenantAuthenticationToken.class);
        AssumeTenantAuthenticationToken resolved = (AssumeTenantAuthenticationToken) grant;
        // account_type (BE-329) + org_scope (BE-338) plumbing fully intact.
        assertThat(resolved.getOperatorAccountType()).isEqualTo("OPERATOR");
        assertThat(resolved.getOrgScope()).containsExactly("dept-sales");
    }

    @Test
    @DisplayName("subject token 무효 → invalid_grant (admin 미호출, mint 없음)")
    void invalidSubjectToken_invalidGrant_noAdminCall() {
        when(subjectTokenDecoder.decode(SUBJECT_TOKEN))
                .thenThrow(new BadJwtException("expired"));

        assertThatThrownBy(() -> provider.authenticate(exchange()))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(e -> assertThat(((OAuth2AuthenticationException) e).getError().getErrorCode())
                        .isEqualTo(OAuth2ErrorCodes.INVALID_GRANT));

        verify(operatorAssignmentPort, never()).resolveAssignment(any(), any());
        verify(tokenGenerator, never()).generate(any());
    }

    @Test
    @DisplayName("assignment-denied (not assigned) → no token, invalid_grant")
    void assignmentDenied_noToken() {
        when(subjectTokenDecoder.decode(SUBJECT_TOKEN)).thenReturn(validSubjectJwt());
        when(operatorAssignmentPort.resolveAssignment(OIDC_SUBJECT, SELECTED_TENANT))
                .thenThrow(new AssumeTenantDeniedException("operator is not assigned to the selected tenant"));

        assertThatThrownBy(() -> provider.authenticate(exchange()))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(e -> assertThat(((OAuth2AuthenticationException) e).getError().getErrorCode())
                        .isEqualTo(OAuth2ErrorCodes.INVALID_GRANT));

        verify(tokenGenerator, never()).generate(any());
    }

    @Test
    @DisplayName("admin-unavailable → fail-closed deny, invalid_grant (NOT fail-soft)")
    void adminUnavailable_failClosedDeny() {
        when(subjectTokenDecoder.decode(SUBJECT_TOKEN)).thenReturn(validSubjectJwt());
        // The port wraps admin-down / timeout / circuit-open into AssumeTenantDeniedException.
        when(operatorAssignmentPort.resolveAssignment(eq(OIDC_SUBJECT), eq(SELECTED_TENANT)))
                .thenThrow(new AssumeTenantDeniedException(
                        "assignment check failed — admin-service unavailable (fail-closed)",
                        new RuntimeException("connection refused")));

        assertThatThrownBy(() -> provider.authenticate(exchange()))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(e -> assertThat(((OAuth2AuthenticationException) e).getError().getErrorCode())
                        .isEqualTo(OAuth2ErrorCodes.INVALID_GRANT));

        verify(tokenGenerator, never()).generate(any());
    }

    @Test
    @DisplayName("supports() → AssumeTenantAuthenticationToken 만 true")
    void supportsOnlyAssumeTenantToken() {
        assertThat(provider.supports(AssumeTenantAuthenticationToken.class)).isTrue();
        assertThat(provider.supports(OAuth2ClientAuthenticationToken.class)).isFalse();
    }
}
