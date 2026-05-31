package com.example.auth.infrastructure.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-BE-327 (ADR-MONO-020 D2) — unit tests for
 * {@link AssumeTenantAuthenticationConverter}: grant_type filtering (returns null
 * → existing grants byte-unchanged) and protocol-level {@code invalid_request}
 * validation.
 */
@DisplayName("AssumeTenantAuthenticationConverter 단위 테스트 (TASK-BE-327)")
class AssumeTenantAuthenticationConverterTest {

    private static final String TOKEN_EXCHANGE = "urn:ietf:params:oauth:grant-type:token-exchange";
    private static final String ACCESS_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:access_token";

    private final AssumeTenantAuthenticationConverter converter = new AssumeTenantAuthenticationConverter();

    private MockHttpServletRequest exchangeRequest() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setParameter("grant_type", TOKEN_EXCHANGE);
        req.setParameter("subject_token", "base-token");
        req.setParameter("subject_token_type", ACCESS_TOKEN_TYPE);
        req.setParameter("audience", "acme-corp");
        return req;
    }

    @Test
    @DisplayName("다른 grant_type → null (기존 grant byte-unchanged)")
    void otherGrant_returnsNull() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setParameter("grant_type", "refresh_token");

        assertThat(converter.convert(req)).isNull();
    }

    @Test
    @DisplayName("grant_type 없음 → null")
    void noGrant_returnsNull() {
        assertThat(converter.convert(new MockHttpServletRequest())).isNull();
    }

    @Test
    @DisplayName("token-exchange + 모든 파라미터 → AssumeTenantAuthenticationToken (선택 tenant + client principal)")
    void validExchange_returnsToken() {
        RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("platform-console-web")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(new org.springframework.security.oauth2.core.AuthorizationGrantType(TOKEN_EXCHANGE))
                .build();
        Authentication clientAuth = new OAuth2ClientAuthenticationToken(
                client, ClientAuthenticationMethod.NONE, null);
        SecurityContextHolder.getContext().setAuthentication(clientAuth);
        try {
            HttpServletRequest req = exchangeRequest();

            Authentication result = converter.convert(req);

            assertThat(result).isInstanceOf(AssumeTenantAuthenticationToken.class);
            AssumeTenantAuthenticationToken token = (AssumeTenantAuthenticationToken) result;
            assertThat(token.getSubjectToken()).isEqualTo("base-token");
            assertThat(token.getSubjectTokenType()).isEqualTo(ACCESS_TOKEN_TYPE);
            assertThat(token.getSelectedTenantId()).isEqualTo("acme-corp");
            assertThat(token.getClientPrincipal()).isSameAs(clientAuth);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("audience 누락 → invalid_request (admin 호출 없음)")
    void missingAudience_invalidRequest() {
        MockHttpServletRequest req = exchangeRequest();
        req.removeParameter("audience");

        assertThatThrownBy(() -> converter.convert(req))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(e -> assertThat(((OAuth2AuthenticationException) e).getError().getErrorCode())
                        .isEqualTo(OAuth2ErrorCodes.INVALID_REQUEST));
    }

    @Test
    @DisplayName("subject_token 누락 → invalid_request")
    void missingSubjectToken_invalidRequest() {
        MockHttpServletRequest req = exchangeRequest();
        req.removeParameter("subject_token");

        assertThatThrownBy(() -> converter.convert(req))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(e -> assertThat(((OAuth2AuthenticationException) e).getError().getErrorCode())
                        .isEqualTo(OAuth2ErrorCodes.INVALID_REQUEST));
    }

    @Test
    @DisplayName("subject_token_type 누락 → invalid_request")
    void missingSubjectTokenType_invalidRequest() {
        MockHttpServletRequest req = exchangeRequest();
        req.removeParameter("subject_token_type");

        assertThatThrownBy(() -> converter.convert(req))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(e -> assertThat(((OAuth2AuthenticationException) e).getError().getErrorCode())
                        .isEqualTo(OAuth2ErrorCodes.INVALID_REQUEST));
    }
}
