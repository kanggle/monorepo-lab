package com.example.auth.infrastructure.oauth2;

import com.example.auth.application.port.OperatorAssignmentPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
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
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TASK-MONO-721 (ADR-MONO-076, ACCEPTED 2026-09-23 — 갈래 D) — the <b>workload</b> branch of
 * {@link AssumeTenantAuthenticationProvider}.
 *
 * <p>🔴 <b>The control is the point of this class, not the success cell.</b> ADR-MONO-076 § D6
 * and TASK-MONO-721 AC-1 both say so: *"「전부 허용」이 되는 것이 이 변경의 가장 비싼 실패이고,
 * 성공 경로만 보는 테스트는 그것을 구조적으로 못 본다"*. So the cell that matters is
 * {@link #refusesTenantOutsideItsCatalog()} — the same credential, the same request shape, one
 * tenant that is not enumerated, refused at the issuer with no token minted.
 *
 * <p>The catalog is used for real (not stubbed). It <em>is</em> the decision this ticket
 * implements; stubbing it would test the plumbing around a fact nobody asserted.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("AssumeTenantAuthenticationProvider 워크로드 분기 (TASK-MONO-721 / ADR-MONO-076 D)")
class WorkloadAssumeTenantProviderTest {

    private static final String WORKLOAD_CLIENT = "product-service-client";
    private static final String SUBJECT_TOKEN = "workload-cc-token";
    private static final String SCOPE = "internal.invoke";
    private static final String ALLOWED_TENANT = "ecommerce";
    /** 🔴 Not in the catalog — the control. */
    private static final String FORBIDDEN_TENANT = "wms";

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
        AuthorizationServerContextHolder.setContext(new AuthorizationServerContext() {
            @Override
            public String getIssuer() {
                return "http://localhost:8081";
            }

            @Override
            public AuthorizationServerSettings getAuthorizationServerSettings() {
                return AuthorizationServerSettings.builder().issuer("http://localhost:8081").build();
            }
        });
    }

    @AfterEach
    void tearDown() {
        AuthorizationServerContextHolder.resetContext();
    }

    // ---------------------------------------------------------------- fixtures

    private static RegisteredClient workloadClient() {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(WORKLOAD_CLIENT)
                .clientSecret("{noop}secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .authorizationGrantType(new AuthorizationGrantType(
                        "urn:ietf:params:oauth:grant-type:token-exchange"))
                .scope(SCOPE)
                .build();
    }

    private static AssumeTenantAuthenticationToken exchangeFor(String tenant) {
        RegisteredClient client = workloadClient();
        Authentication principal = new OAuth2ClientAuthenticationToken(
                client, ClientAuthenticationMethod.CLIENT_SECRET_BASIC, "secret");
        return new AssumeTenantAuthenticationToken(
                principal, SUBJECT_TOKEN,
                "urn:ietf:params:oauth:token-type:access_token", tenant);
    }

    /** A workload {@code client_credentials} token: {@code aud} is the client, scope is granted. */
    private static Jwt workloadSubjectJwt() {
        return Jwt.withTokenValue(SUBJECT_TOKEN)
                .header("alg", "RS256")
                .subject(WORKLOAD_CLIENT)
                .audience(List.of(WORKLOAD_CLIENT))
                .claim("tenant_id", "global-account-platform")
                .claim("scope", List.of(SCOPE))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }

    private void stubMint() {
        Jwt minted = Jwt.withTokenValue("assumed-workload-token")
                .header("alg", "RS256")
                .subject(WORKLOAD_CLIENT)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(1800))
                .build();
        doReturn(minted).when(tokenGenerator).generate(any());
    }

    // ---------------------------------------------------------------- the control

    @Test
    @DisplayName("🔴 대조군 — 카탈로그에 없는 테넌트는 **발급자에서** 거절된다 (토큰 없음)")
    void refusesTenantOutsideItsCatalog() {
        when(subjectTokenDecoder.decode(SUBJECT_TOKEN)).thenReturn(workloadSubjectJwt());

        assertThatThrownBy(() -> provider.authenticate(exchangeFor(FORBIDDEN_TENANT)))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(e -> assertThat(((OAuth2AuthenticationException) e).getError().getErrorCode())
                        .as("AC-7: the refusal is invalid_grant at the issuer, not a 403 at an edge")
                        .isEqualTo(OAuth2ErrorCodes.INVALID_GRANT));

        // 🔴 No token was minted. An error response that still minted would be the "전부 허용"
        // failure wearing a refusal's clothes.
        verify(tokenGenerator, never()).generate(any());
    }

    @Test
    @DisplayName("🔴 대조군 ② — 같은 자격·같은 요청 모양, 허용 테넌트면 **성공**한다 (거절이 자격 탓이 아님)")
    void sameCredentialSucceedsForAnAllowedTenant() {
        when(subjectTokenDecoder.decode(SUBJECT_TOKEN)).thenReturn(workloadSubjectJwt());
        stubMint();

        Authentication result = provider.authenticate(exchangeFor(ALLOWED_TENANT));

        assertThat(result).isInstanceOf(OAuth2AccessTokenAuthenticationToken.class);
        assertThat(((OAuth2AccessTokenAuthenticationToken) result).getAccessToken().getTokenValue())
                .isEqualTo("assumed-workload-token");
        // Short-lived, no refresh — ADR-MONO-076 keeps ADR-MONO-020 § 3.1's shape.
        assertThat(((OAuth2AccessTokenAuthenticationToken) result).getRefreshToken()).isNull();
    }

    // ---------------------------------------------------------------- D4: not an identity

    @Test
    @DisplayName("D4 — 워크로드 분기는 **운영자 배정 포트를 부르지 않는다**, 그리고 별도 grant 타입으로 민트한다")
    void doesNotAskAdminServiceAndUsesTheWorkloadGrantType() {
        when(subjectTokenDecoder.decode(SUBJECT_TOKEN)).thenReturn(workloadSubjectJwt());
        stubMint();

        provider.authenticate(exchangeFor(ALLOWED_TENANT));

        // A workload has no operator assignment; asking admin-service would be the wrong
        // question and would couple a machine path to a service it must not depend on.
        verifyNoInteractions(operatorAssignmentPort);

        ArgumentCaptor<OAuth2TokenContext> ctx = ArgumentCaptor.forClass(OAuth2TokenContext.class);
        verify(tokenGenerator).generate(ctx.capture());

        // 🔴 The customizer tells the two shapes apart by THIS type. If the provider ever
        // reverts to the operator token, the customizer silently applies operator
        // derivations (entitled_domains / derived roles / org_scope / sub alignment) to a
        // machine credential — the most expensive way ADR-MONO-076 can fail.
        Object resolvedGrant = ctx.getValue().getAuthorizationGrant();
        assertThat(resolvedGrant).isInstanceOf(WorkloadAssumeTenantAuthenticationToken.class);
        WorkloadAssumeTenantAuthenticationToken grant =
                (WorkloadAssumeTenantAuthenticationToken) resolvedGrant;
        assertThat(grant.getSelectedTenantId()).isEqualTo(ALLOWED_TENANT);
        assertThat(grant.getClientId()).isEqualTo(WORKLOAD_CLIENT);
    }

    // ---------------------------------------------------------------- the four gates

    @Test
    @DisplayName("게이트 1 — subject_token 이 유효하지 않으면 invalid_grant (민트 없음)")
    void invalidSubjectTokenIsRefused() {
        when(subjectTokenDecoder.decode(SUBJECT_TOKEN)).thenThrow(new BadJwtException("bad"));

        assertThatThrownBy(() -> provider.authenticate(exchangeFor(ALLOWED_TENANT)))
                .isInstanceOf(OAuth2AuthenticationException.class);
        verify(tokenGenerator, never()).generate(any());
    }

    @Test
    @DisplayName("🔴 게이트 2 — 남의 토큰으로는 못 바꾼다: aud 가 이 클라이언트가 아니면 거절")
    void subjectTokenIssuedToAnotherClientIsRefused() {
        Jwt someoneElses = Jwt.withTokenValue(SUBJECT_TOKEN)
                .header("alg", "RS256")
                .subject("account-service-client")
                .audience(List.of("account-service-client"))
                .claim("scope", List.of(SCOPE))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        when(subjectTokenDecoder.decode(SUBJECT_TOKEN)).thenReturn(someoneElses);

        // Without this gate the scope intersection below would read a scope set that was
        // never granted to THIS client — a borrowed-scope escalation, not a tenant one.
        assertThatThrownBy(() -> provider.authenticate(exchangeFor(ALLOWED_TENANT)))
                .isInstanceOf(OAuth2AuthenticationException.class);
        verify(tokenGenerator, never()).generate(any());
    }

    @Test
    @DisplayName("게이트 3 — 등록된 scope 가 토큰에 없으면 거절 (등록이 아니라 요청이 정한다)")
    void subjectTokenWithoutARegisteredScopeIsRefused() {
        Jwt noScope = Jwt.withTokenValue(SUBJECT_TOKEN)
                .header("alg", "RS256")
                .subject(WORKLOAD_CLIENT)
                .audience(List.of(WORKLOAD_CLIENT))
                .claim("scope", List.of("something.else"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        when(subjectTokenDecoder.decode(SUBJECT_TOKEN)).thenReturn(noScope);

        assertThatThrownBy(() -> provider.authenticate(exchangeFor(ALLOWED_TENANT)))
                .isInstanceOf(OAuth2AuthenticationException.class);
        verify(tokenGenerator, never()).generate(any());
    }

    @Test
    @DisplayName("게이트 3b — 민트되는 것은 **교집합**이지 등록된 scope 전체가 아니다")
    void mintsTheIntersectionNotTheRegistration() {
        // The subject token carries one granted scope plus one the client is not registered
        // for; the registration carries only `internal.invoke`. Both directions are trimmed.
        Jwt wider = Jwt.withTokenValue(SUBJECT_TOKEN)
                .header("alg", "RS256")
                .subject(WORKLOAD_CLIENT)
                .audience(List.of(WORKLOAD_CLIENT))
                .claim("scope", List.of(SCOPE, "not.registered"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        when(subjectTokenDecoder.decode(SUBJECT_TOKEN)).thenReturn(wider);
        stubMint();

        Authentication result = provider.authenticate(exchangeFor(ALLOWED_TENANT));

        assertThat(((OAuth2AccessTokenAuthenticationToken) result).getAccessToken().getScopes())
                .containsExactly(SCOPE);
    }

    @Test
    @DisplayName("게이트 3c — scope 가 RFC 6749 의 공백 구분 문자열로 와도 읽는다")
    void readsTheSpaceDelimitedScopeShapeToo() {
        // 🔴 A reader that handles only the JSON-array shape returns an empty set here, and an
        // empty set means invalid_grant — the wrong shape would look exactly like "the caller
        // asked for nothing", which is the hardest kind of bug to see from the outside.
        Jwt spaceDelimited = Jwt.withTokenValue(SUBJECT_TOKEN)
                .header("alg", "RS256")
                .subject(WORKLOAD_CLIENT)
                .audience(List.of(WORKLOAD_CLIENT))
                .claim("scope", SCOPE + " not.registered")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        when(subjectTokenDecoder.decode(SUBJECT_TOKEN)).thenReturn(spaceDelimited);
        stubMint();

        Authentication result = provider.authenticate(exchangeFor(ALLOWED_TENANT));

        assertThat(((OAuth2AccessTokenAuthenticationToken) result).getAccessToken().getScopes())
                .containsExactly(SCOPE);
    }

    // ---------------------------------------------------------------- the operator path is intact

    @Test
    @DisplayName("🔵 열거되지 않은 클라이언트는 **운영자 분기 그대로** — 배정 게이트가 답한다")
    void unenumeratedClientStillTakesTheOperatorBranch() {
        RegisteredClient console = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("platform-console-web")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(new AuthorizationGrantType(
                        "urn:ietf:params:oauth:grant-type:token-exchange"))
                .build();
        Authentication principal = new OAuth2ClientAuthenticationToken(
                console, ClientAuthenticationMethod.NONE, null);
        AssumeTenantAuthenticationToken operatorExchange = new AssumeTenantAuthenticationToken(
                principal, SUBJECT_TOKEN,
                "urn:ietf:params:oauth:token-type:access_token", ALLOWED_TENANT);

        String accountId = "00000000-0000-7000-8000-0000000000a1";
        when(subjectTokenDecoder.decode(SUBJECT_TOKEN)).thenReturn(
                Jwt.withTokenValue(SUBJECT_TOKEN)
                        .header("alg", "RS256")
                        .subject(accountId)
                        .claim("tenant_id", "iam")
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(300))
                        .build());
        when(operatorAssignmentPort.resolveAssignment(accountId, ALLOWED_TENANT))
                .thenReturn(new OperatorAssignmentPort.AssignmentResult(true, null));
        stubMint();

        provider.authenticate(operatorExchange);

        // The gate that decides the operator path is still the one that decided it before —
        // this ticket adds a branch, it does not move the existing one.
        verify(operatorAssignmentPort).resolveAssignment(accountId, ALLOWED_TENANT);
    }
}
