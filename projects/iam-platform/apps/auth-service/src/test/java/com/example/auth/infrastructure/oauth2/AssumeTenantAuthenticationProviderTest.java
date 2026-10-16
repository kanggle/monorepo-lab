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

    // TASK-MONO-263 (ADR-032 D5 step 4): the operator's account_type is no longer
    // read from the subject token or carried onto the resolved grant — the
    // preservesOperatorAccountType_onResolvedGrant test (TASK-BE-329) is deleted.

    @Test
    @DisplayName("TASK-MONO-299: account_id-only — the validated sub (= account UUID) is "
            + "threaded to the port directly (no email resolution)")
    void resolvesByAccountIdOnly() {
        // ADR-MONO-040 Phase 3 part B: admin_operators.oidc_subject is backfilled to
        // account_id (part A), so the provider resolves on the validated sub directly —
        // no server-side email lookup, no email param to the port.
        Jwt subjectNoEmailClaim = validSubjectJwt();
        assertThat(subjectNoEmailClaim.getClaimAsString("email")).isNull(); // the real producer shape
        when(subjectTokenDecoder.decode(SUBJECT_TOKEN)).thenReturn(subjectNoEmailClaim);
        when(operatorAssignmentPort.resolveAssignment(OIDC_SUBJECT, SELECTED_TENANT))
                .thenReturn(new OperatorAssignmentPort.AssignmentResult(true, null));
        Jwt minted = Jwt.withTokenValue("assumed-token")
                .header("alg", "RS256").subject(OIDC_SUBJECT)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(1800)).build();
        doReturn(minted).when(tokenGenerator).generate(any());

        Authentication result = provider.authenticate(exchange());

        assertThat(result).isInstanceOf(OAuth2AccessTokenAuthenticationToken.class);
        // The port was called with the account_id sub only — no email argument.
        verify(operatorAssignmentPort).resolveAssignment(OIDC_SUBJECT, SELECTED_TENANT);
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
        // longer preserves them — the resolved grant carries only org_scope (BE-338);
        // the customizer DERIVES roles from the selected tenant's entitled domains.
        // TASK-MONO-263: account_type is no longer read/threaded either.
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
        // org_scope (BE-338) plumbing fully intact (account_type removed — MONO-263).
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

    // ── TASK-MONO-771 S4 — second-factor gate (auth-api.md § Assume-Tenant Exchange, OD-2) ──────

    private Jwt subjectWithAmr(java.util.List<String> amr) {
        Jwt.Builder b = Jwt.withTokenValue(SUBJECT_TOKEN)
                .header("alg", "RS256")
                .subject(OIDC_SUBJECT)
                .claim("tenant_id", "iam")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        if (amr != null) {
            b.claim("amr", amr);
        }
        return b.build();
    }

    private void stubMint() {
        Jwt minted = Jwt.withTokenValue("assumed-token")
                .header("alg", "RS256").subject(OIDC_SUBJECT)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(1800)).build();
        doReturn(minted).when(tokenGenerator).generate(any());
    }

    private static void assertInsufficientUserAuthentication(Throwable e) {
        assertThat(e).isInstanceOf(OAuth2AuthenticationException.class);
        var error = ((OAuth2AuthenticationException) e).getError();
        assertThat(error.getErrorCode()).isEqualTo(OAuth2ErrorCodes.INVALID_GRANT);
        // whole-value equality — the contract's fixed discriminator (HS-C)
        assertThat(error.getDescription()).isEqualTo("insufficient_user_authentication");
    }

    @Test
    @DisplayName("AC-1b (F2): platform operator, mfaRequired=true, amr=[pwd] → invalid_grant + insufficient_user_authentication, no mint")
    void mfaRequired_withoutMfa_refused() {
        when(subjectTokenDecoder.decode(SUBJECT_TOKEN)).thenReturn(subjectWithAmr(java.util.List.of("pwd")));
        when(operatorAssignmentPort.resolveAssignment(OIDC_SUBJECT, SELECTED_TENANT))
                .thenReturn(new OperatorAssignmentPort.AssignmentResult(true, null, null, true));

        assertThatThrownBy(() -> provider.authenticate(exchange()))
                .satisfies(AssumeTenantAuthenticationProviderTest::assertInsufficientUserAuthentication);
        verify(tokenGenerator, never()).generate(any());
    }

    @Test
    @DisplayName("AC-1b: mfaRequired=true and NO amr claim → refused (absent = no second factor)")
    void mfaRequired_amrAbsent_refused() {
        when(subjectTokenDecoder.decode(SUBJECT_TOKEN)).thenReturn(subjectWithAmr(null));
        when(operatorAssignmentPort.resolveAssignment(OIDC_SUBJECT, SELECTED_TENANT))
                .thenReturn(new OperatorAssignmentPort.AssignmentResult(true, null, null, true));

        assertThatThrownBy(() -> provider.authenticate(exchange()))
                .satisfies(AssumeTenantAuthenticationProviderTest::assertInsufficientUserAuthentication);
        verify(tokenGenerator, never()).generate(any());
    }

    @Test
    @DisplayName("AC-1b / AC-2: mfaRequired=true and amr ∋ mfa → minted")
    void mfaRequired_withMfa_minted() {
        when(subjectTokenDecoder.decode(SUBJECT_TOKEN))
                .thenReturn(subjectWithAmr(java.util.List.of("pwd", "otp", "mfa")));
        when(operatorAssignmentPort.resolveAssignment(OIDC_SUBJECT, SELECTED_TENANT))
                .thenReturn(new OperatorAssignmentPort.AssignmentResult(true, null, null, true));
        stubMint();

        assertThat(provider.authenticate(exchange())).isInstanceOf(OAuth2AccessTokenAuthenticationToken.class);
    }

    @Test
    @DisplayName("AC-2 control: tenant without policy (mfaRequired=false) → same amr=[pwd] subject is minted")
    void mfaNotRequired_controlTenant_minted() {
        when(subjectTokenDecoder.decode(SUBJECT_TOKEN)).thenReturn(subjectWithAmr(java.util.List.of("pwd")));
        when(operatorAssignmentPort.resolveAssignment(OIDC_SUBJECT, SELECTED_TENANT))
                .thenReturn(new OperatorAssignmentPort.AssignmentResult(true, null, null, false));
        stubMint();

        assertThat(provider.authenticate(exchange())).isInstanceOf(OAuth2AccessTokenAuthenticationToken.class);
    }

    @Test
    @DisplayName("MONO-771: the not-assigned refusal does NOT carry insufficient_user_authentication (distinct discriminator)")
    void notAssigned_isNotTheStepUpDiscriminator() {
        when(subjectTokenDecoder.decode(SUBJECT_TOKEN)).thenReturn(subjectWithAmr(java.util.List.of("pwd")));
        when(operatorAssignmentPort.resolveAssignment(OIDC_SUBJECT, SELECTED_TENANT))
                .thenThrow(new AssumeTenantDeniedException("operator is not assigned to the selected tenant"));

        assertThatThrownBy(() -> provider.authenticate(exchange()))
                .satisfies(e -> assertThat(((OAuth2AuthenticationException) e).getError().getDescription())
                        .isNotEqualTo("insufficient_user_authentication"));
    }

    @Test
    @DisplayName("supports() → AssumeTenantAuthenticationToken 만 true")
    void supportsOnlyAssumeTenantToken() {
        assertThat(provider.supports(AssumeTenantAuthenticationToken.class)).isTrue();
        assertThat(provider.supports(OAuth2ClientAuthenticationToken.class)).isFalse();
    }

    // -----------------------------------------------------------------------
    // TASK-BE-632 (ADR-MONO-083 D6 · D7 · OD-2)
    // -----------------------------------------------------------------------

    private org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext mintAndCapture(
            OperatorAssignmentPort.AssignmentResult result) {
        when(subjectTokenDecoder.decode(SUBJECT_TOKEN)).thenReturn(validSubjectJwt());
        when(operatorAssignmentPort.resolveAssignment(OIDC_SUBJECT, SELECTED_TENANT)).thenReturn(result);
        Jwt minted = Jwt.withTokenValue("assumed-token")
                .header("alg", "RS256").subject(OIDC_SUBJECT)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        doReturn(minted).when(tokenGenerator).generate(any());
        provider.authenticate(exchange());
        org.mockito.ArgumentCaptor<org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext> captor =
                org.mockito.ArgumentCaptor.forClass(
                        org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext.class);
        verify(tokenGenerator).generate(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("BE-632: admin-service domainRoles ride onto the resolved grant (pre-intersection — the customizer filters)")
    void carriesDomainRoles_onResolvedGrant() {
        var ctx = mintAndCapture(new OperatorAssignmentPort.AssignmentResult(
                true, null, null, false, java.util.List.of("INVENTORY_READ", "INVENTORY_WRITE")));
        AssumeTenantAuthenticationToken grant = (AssumeTenantAuthenticationToken) ctx.getAuthorizationGrant();
        assertThat(grant.getDomainRoles()).containsExactly("INVENTORY_READ", "INVENTORY_WRITE");
    }

    @Test
    @DisplayName("BE-632: domainRoles absent → null on the grant (the customizer reads it as roles 0)")
    void carriesAbsentDomainRoles_asNull() {
        var ctx = mintAndCapture(new OperatorAssignmentPort.AssignmentResult(true, null, null, false));
        assertThat(((AssumeTenantAuthenticationToken) ctx.getAuthorizationGrant()).getDomainRoles()).isNull();
    }

    @Test
    @DisplayName("🔴 BE-632 AC-5: the operator assumed token never carries erp.write; every other registered scope stays")
    void operatorAssumedScopes_dropErpWrite() {
        RegisteredClient client = RegisteredClient.withId("c1").clientId("platform-console-web")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://console.example/cb")
                .scope("openid").scope("profile").scope("tenant.read").scope("erp.write")
                .build();
        assertThat(AssumeTenantAuthenticationProvider.operatorAssumedScopes(client))
                .containsExactlyInAnyOrder("openid", "profile", "tenant.read")
                .doesNotContain("erp.write");
        // The registration itself is untouched (the base token path still reads it).
        assertThat(client.getScopes()).contains("erp.write");
    }

    @Test
    @DisplayName("🔴 TASK-MONO-794: finance.write is excluded from the operator assumed token too (guard — not registered on the console client today)")
    void operatorAssumedScopes_dropFinanceWrite() {
        assertThat(AssumeTenantAuthenticationProvider.OPERATOR_EXCLUDED_SCOPES)
                .containsExactlyInAnyOrder("erp.write", "finance.write");
        // a hypothetical registration that carried finance.write (and finance.read) — the write scope is dropped,
        // the read scope rides on (reading is what the business READ roles already allow; the hole is writes)
        RegisteredClient client = RegisteredClient.withId("c3").clientId("platform-console-web")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://console.example/cb")
                .scope("openid").scope("tenant.read").scope("finance.read").scope("finance.write")
                .build();
        assertThat(AssumeTenantAuthenticationProvider.operatorAssumedScopes(client))
                .containsExactlyInAnyOrder("openid", "tenant.read", "finance.read")
                .doesNotContain("finance.write");
    }

    @Test
    @DisplayName("🔴 BE-632 OD-2: the operator assumed token lives min(client TTL, 300 s) — a 1800 s client is capped, a shorter one kept")
    void withOperatorAssumedTtl_capsAt300() {
        RegisteredClient longLived = RegisteredClient.withId("c2").clientId("platform-console-web")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://console.example/cb")
                .tokenSettings(org.springframework.security.oauth2.server.authorization.settings.TokenSettings.builder()
                        .accessTokenTimeToLive(java.time.Duration.ofSeconds(1800)).build())
                .build();
        assertThat(AssumeTenantAuthenticationProvider.withOperatorAssumedTtl(longLived)
                .getTokenSettings().getAccessTokenTimeToLive()).isEqualTo(java.time.Duration.ofSeconds(300));
        // the registration itself keeps 1800 s — base IAM token and refresh are unchanged
        assertThat(longLived.getTokenSettings().getAccessTokenTimeToLive())
                .isEqualTo(java.time.Duration.ofSeconds(1800));

        RegisteredClient shortLived = RegisteredClient.from(longLived)
                .tokenSettings(org.springframework.security.oauth2.server.authorization.settings.TokenSettings.builder()
                        .accessTokenTimeToLive(java.time.Duration.ofSeconds(120)).build())
                .build();
        assertThat(AssumeTenantAuthenticationProvider.withOperatorAssumedTtl(shortLived)
                .getTokenSettings().getAccessTokenTimeToLive()).isEqualTo(java.time.Duration.ofSeconds(120));
    }

    @Test
    @DisplayName("BE-632: the mint context carries the capped TTL and no erp.write (what JwtGenerator actually reads)")
    void mintContext_ttlCappedAndNoErpWrite() {
        var ctx = mintAndCapture(new OperatorAssignmentPort.AssignmentResult(true, null, null, false, java.util.List.of()));
        assertThat(ctx.getRegisteredClient().getTokenSettings().getAccessTokenTimeToLive())
                .isLessThanOrEqualTo(java.time.Duration.ofSeconds(300));
        assertThat(ctx.getAuthorizedScopes()).doesNotContain("erp.write");
    }
}
