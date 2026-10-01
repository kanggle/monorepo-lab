package com.example.auth.infrastructure.oauth2;

import com.example.auth.application.port.OperatorAssignmentPort;
import com.example.auth.domain.tenant.TenantContext;
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
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2Token;
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
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * TASK-BE-614 — the assume-tenant exchange never yields a token for {@code consumer-pool}, even when
 * an operator assignment to it EXISTS (the control: the assignment gate would say yes; the token
 * still must not be minted). account-service V0029 made the tenant row real and ACTIVE, and admin
 * assignment creation does not validate the tenant, so such a row is reachable.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("AssumeTenantAuthenticationProvider — consumer-pool 은 assume 할 수 없다 (TASK-BE-614)")
class AssumeTenantConsumerPoolRefusalTest {

    private static final String SUBJECT_TOKEN = "base-iam-oidc-token";
    private static final String OIDC_SUBJECT = "00000000-0000-7000-8000-0000000000a1";

    @Mock private JwtDecoder subjectTokenDecoder;
    @Mock private OperatorAssignmentPort operatorAssignmentPort;
    @Mock private OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator;

    private AssumeTenantAuthenticationProvider provider;

    @BeforeEach
    void setUp() {
        provider = new AssumeTenantAuthenticationProvider(subjectTokenDecoder, operatorAssignmentPort, tokenGenerator);
        AuthorizationServerContextHolder.setContext(new AuthorizationServerContext() {
            @Override public String getIssuer() { return "http://localhost:8081"; }
            @Override public AuthorizationServerSettings getAuthorizationServerSettings() {
                return AuthorizationServerSettings.builder().issuer("http://localhost:8081").build();
            }
        });
        // "the assignment row exists": the admin-service gate WOULD approve any tenant, including
        // the pool. lenient — for the pool case the provider must refuse before ever asking.
        lenient().when(operatorAssignmentPort.resolveAssignment(anyString(), anyString()))
                .thenReturn(new OperatorAssignmentPort.AssignmentResult(true, null));
        lenient().when(subjectTokenDecoder.decode(SUBJECT_TOKEN)).thenReturn(Jwt.withTokenValue(SUBJECT_TOKEN)
                .header("alg", "RS256").subject(OIDC_SUBJECT).claim("tenant_id", "iam")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build());
    }

    @AfterEach
    void tearDown() {
        AuthorizationServerContextHolder.resetContext();
    }

    private static AssumeTenantAuthenticationToken exchange(String clientId, String selectedTenant) {
        RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(new AuthorizationGrantType("urn:ietf:params:oauth:grant-type:token-exchange"))
                .build();
        Authentication clientPrincipal = new OAuth2ClientAuthenticationToken(client, ClientAuthenticationMethod.NONE, null);
        return new AssumeTenantAuthenticationToken(clientPrincipal, SUBJECT_TOKEN,
                "urn:ietf:params:oauth:token-type:access_token", selectedTenant);
    }

    @Test
    @DisplayName("배정 행이 있어도 consumer-pool 선택은 invalid_grant — 게이트도 생성기도 호출 안 함")
    void poolSelected_refused_evenWithAssignment() {
        Throwable thrown = catchThrowable(() ->
                provider.authenticate(exchange("platform-console-web", TenantContext.CONSUMER_POOL_TENANT_ID)));

        assertThat(thrown).isInstanceOf(OAuth2AuthenticationException.class);
        assertThat(((OAuth2AuthenticationException) thrown).getError().getErrorCode())
                .isEqualTo(OAuth2ErrorCodes.INVALID_GRANT);
        verify(tokenGenerator, never()).generate(any());
        verify(operatorAssignmentPort, never()).resolveAssignment(anyString(), anyString());
    }

    @Test
    @DisplayName("대조군: 같은 배정 · 같은 클라이언트로 일반 고객 테넌트(acme-corp)는 발급된다")
    void ordinaryTenant_withSameAssignment_mints() {
        doReturn(Jwt.withTokenValue("assumed").header("alg", "RS256").subject(OIDC_SUBJECT)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(1800)).build())
                .when(tokenGenerator).generate(any());

        Authentication result = provider.authenticate(exchange("platform-console-web", "acme-corp"));

        assertThat(result).isInstanceOf(OAuth2AccessTokenAuthenticationToken.class);
        assertThat(((OAuth2AccessTokenAuthenticationToken) result).getAccessToken().getTokenValue())
                .isEqualTo("assumed");
    }
}
