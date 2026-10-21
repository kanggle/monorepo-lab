package com.example.auth.infrastructure.oauth2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.authorization.oidc.web.OidcLogoutEndpointFilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * TASK-BE-641 — {@link OidcLogoutErrorResponseHandler} behind SAS 1.4.1's real {@link OidcLogoutEndpointFilter}
 * (its real default request converter). Runs without Docker.
 *
 * <p>What this proves and what it does not: that the filter hands its rejections to the handler and that the
 * handler writes the 400 OIDC body itself — {@code getErrorMessage()} stays {@code null}, i.e. no
 * {@code sendError}, so no container {@code /error} dispatch can follow. That the dispatch is what turned the 400
 * into Order 2's 401 is shown only on a real server: {@code OidcLogoutEndpointRealServerIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class OidcLogoutErrorResponseHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private FilterChain chain;

    private OidcLogoutEndpointFilter filter() {
        OidcLogoutEndpointFilter filter = new OidcLogoutEndpointFilter(authenticationManager);
        filter.setAuthenticationFailureHandler(new OidcLogoutErrorResponseHandler());
        return filter;
    }

    private static MockHttpServletRequest logoutRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/connect/logout");
        request.setServletPath("/connect/logout");
        return request;
    }

    @Test
    @DisplayName("id_token_hint 없음 → 400 application/json, 본문은 error · error_description 둘뿐, sendError 없음")
    void missingHint_writes400OidcBodyDirectly() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(logoutRequest(), response, chain);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getErrorMessage()).as("no sendError → no /error dispatch").isNull();
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("error").asText()).isEqualTo("invalid_request");
        assertThat(body.get("error_description").asText())
                .isEqualTo("OpenID Connect 1.0 Logout Request Parameter: id_token_hint");
        assertThat(body.size()).as(response.getContentAsString()).isEqualTo(2);
        verifyNoInteractions(chain);
        verifyNoInteractions(authenticationManager);
    }

    @Test
    @DisplayName("provider 거절(invalid_token · sid) → 400, error_uri 는 싣지 않는다")
    void providerRejection_writesItsCode_withoutErrorUri() throws Exception {
        given(authenticationManager.authenticate(any(Authentication.class))).willThrow(
                new OAuth2AuthenticationException(new OAuth2Error("invalid_token",
                        "OpenID Connect 1.0 Logout Request Parameter: sid",
                        "https://openid.net/specs/openid-connect-rpinitiated-1_0.html#ValidationAndErrorHandling")));
        MockHttpServletRequest request = logoutRequest();
        // SAS reads GET parameters from the query string (OAuth2EndpointUtils.getQueryParameters).
        request.setQueryString("id_token_hint=some.id.token");
        request.setParameter("id_token_hint", "some.id.token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getErrorMessage()).isNull();
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("error").asText()).isEqualTo("invalid_token");
        assertThat(body.get("error_description").asText())
                .isEqualTo("OpenID Connect 1.0 Logout Request Parameter: sid");
        assertThat(body.has("error_uri")).isFalse();
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("error_description 은 200자로 자른다")
    void longDescription_isBounded() {
        String longText = "x".repeat(OidcLogoutErrorResponseHandler.MAX_DESCRIPTION_LENGTH + 50);

        OAuth2Error bounded = OidcLogoutErrorResponseHandler.toBoundedError(
                new OAuth2AuthenticationException(new OAuth2Error("invalid_request", longText, null)));

        assertThat(bounded.getDescription()).hasSize(OidcLogoutErrorResponseHandler.MAX_DESCRIPTION_LENGTH);
    }

    @Test
    @DisplayName("OAuth2 가 아닌 인증 예외 → invalid_request (설명 없음, 예외 메시지를 싣지 않는다)")
    void nonOAuth2Exception_mapsToInvalidRequest() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new OidcLogoutErrorResponseHandler().onAuthenticationFailure(
                logoutRequest(), response, new BadCredentialsException("internal detail"));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).isEqualTo("{\"error\":\"invalid_request\"}");
    }
}
