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
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.web.OAuth2AuthorizationEndpointFilter;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * TASK-BE-642 — {@link AuthorizationEndpointErrorResponseHandler} behind SAS 1.4.1's real
 * {@link OAuth2AuthorizationEndpointFilter} (its real default request converter). Runs without Docker.
 *
 * <p>Two things are pinned: the non-redirectable branch writes the 400 body itself ({@code getErrorMessage()}
 * stays {@code null} — no {@code sendError}, so no container {@code /error} dispatch), and the redirectable
 * branch produces exactly the {@code Location} the SAS default produces for the same rejection — measured by
 * running the same request through a filter with the SAS default handler. That the {@code /error} dispatch is
 * what turned the 400 into Order 2's 401 is shown only on a real server:
 * {@code AuthorizeEndpointErrorRealServerIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class AuthorizationEndpointErrorResponseHandlerTest {

    private static final String AUTHORIZE = "/oauth2/authorize";
    private static final String REDIRECT_URI = "http://localhost:3000/api/auth/callback";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private FilterChain chain;

    private OAuth2AuthorizationEndpointFilter filterWithHandler() {
        OAuth2AuthorizationEndpointFilter filter = new OAuth2AuthorizationEndpointFilter(authenticationManager);
        filter.setAuthenticationFailureHandler(new AuthorizationEndpointErrorResponseHandler());
        return filter;
    }

    /** SAS 1.4.1 unchanged — the reference for the redirect branch and the witness of the defect. */
    private OAuth2AuthorizationEndpointFilter filterWithSasDefault() {
        return new OAuth2AuthorizationEndpointFilter(authenticationManager);
    }

    private static MockHttpServletRequest authorizeRequest(String query) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", AUTHORIZE);
        request.setServletPath(AUTHORIZE);
        // SAS reads GET parameters from the query string (OAuth2EndpointUtils.getQueryParameters).
        request.setQueryString(query);
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            request.addParameter(kv[0], kv.length > 1 ? kv[1] : "");
        }
        return request;
    }

    private static OAuth2AuthorizationCodeRequestAuthenticationException rejection(
            String errorCode, String parameter, String redirectUri, String state) {
        OAuth2AuthorizationCodeRequestAuthenticationToken token = new OAuth2AuthorizationCodeRequestAuthenticationToken(
                "http://localhost" + AUTHORIZE, "platform-console-web",
                new TestingAuthenticationToken("anonymous", null), redirectUri, state, Set.of("openid"), Map.of());
        return new OAuth2AuthorizationCodeRequestAuthenticationException(
                new OAuth2Error(errorCode, "OAuth 2.0 Parameter: " + parameter,
                        "https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.2.1"),
                token);
    }

    private JsonNode assertOAuthJson400(MockHttpServletResponse response) throws Exception {
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getErrorMessage()).as("no sendError → no /error dispatch").isNull();
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getRedirectedUrl()).isNull();
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.size()).as(response.getContentAsString()).isEqualTo(2);
        return body;
    }

    @Test
    @DisplayName("converter 오류(client_id 없음) → 400 JSON {invalid_request, …client_id}, sendError 없음 · AM 미호출")
    void converterRejection_writes400Directly() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filterWithHandler().doFilter(authorizeRequest("response_type=code"), response, chain);

        JsonNode body = assertOAuthJson400(response);
        assertThat(body.get("error").asText()).isEqualTo("invalid_request");
        assertThat(body.get("error_description").asText()).isEqualTo("OAuth 2.0 Parameter: client_id");
        verifyNoInteractions(chain);
        verifyNoInteractions(authenticationManager);
    }

    @Test
    @DisplayName("SAS 기본은 같은 요청에 sendError(400) — 그 /error 디스패치가 401 이 된 자리 (결함의 증인)")
    void sasDefault_sendsErrorForTheSameRequest() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filterWithSasDefault().doFilter(authorizeRequest("response_type=code"), response, chain);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getErrorMessage()).as("sendError → container /error dispatch").isNotNull();
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    @DisplayName("provider 거절, redirect_uri 비움(모르는 client · 미등록 redirect_uri) → 400 JSON, error_uri · 요청 값 없음")
    void providerRejectionWithoutRedirectUri_writes400_withoutErrorUriOrRequestValues() throws Exception {
        given(authenticationManager.authenticate(any(Authentication.class)))
                .willThrow(rejection("invalid_request", "redirect_uri", null, "s-1"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filterWithHandler().doFilter(authorizeRequest(
                "response_type=code&client_id=platform-console-web&redirect_uri=https://attacker.example/cb"
                        + "&state=s-1"), response, chain);

        JsonNode body = assertOAuthJson400(response);
        assertThat(body.get("error").asText()).isEqualTo("invalid_request");
        assertThat(body.get("error_description").asText()).isEqualTo("OAuth 2.0 Parameter: redirect_uri");
        assertThat(body.has("error_uri")).isFalse();
        assertThat(response.getContentAsString())
                .doesNotContain("attacker.example")
                .doesNotContain("platform-console-web")
                .doesNotContain("s-1");
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("redirect 형(invalid_scope, redirect_uri 있음) → SAS 기본과 같은 Location 으로 302")
    void redirectableRejection_redirectsExactlyLikeTheSasDefault() throws Exception {
        // A state with characters that must be encoded — the branch must encode exactly as SAS does.
        given(authenticationManager.authenticate(any(Authentication.class)))
                .willThrow(rejection("invalid_scope", "scope", REDIRECT_URI, "be 642&x=y"));
        String query = "response_type=code&client_id=platform-console-web&scope=openid";

        MockHttpServletResponse ours = new MockHttpServletResponse();
        filterWithHandler().doFilter(authorizeRequest(query), ours, chain);
        MockHttpServletResponse sas = new MockHttpServletResponse();
        filterWithSasDefault().doFilter(authorizeRequest(query), sas, chain);

        assertThat(sas.getStatus()).isEqualTo(302);
        assertThat(ours.getStatus()).isEqualTo(302);
        assertThat(ours.getRedirectedUrl()).isEqualTo(sas.getRedirectedUrl());
        assertThat(ours.getRedirectedUrl())
                .startsWith(REDIRECT_URI + "?error=invalid_scope&error_description=OAuth%202.0%20Parameter%3A%20scope")
                .contains("&error_uri=")
                .endsWith("&state=be%20642%26x%3Dy");
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("redirect 형(login_required, state 없음) → SAS 기본과 같은 Location")
    void redirectableRejectionWithoutState_matchesSasDefault() throws Exception {
        given(authenticationManager.authenticate(any(Authentication.class)))
                .willThrow(rejection("login_required", "prompt", REDIRECT_URI, null));
        String query = "response_type=code&client_id=platform-console-web&scope=openid&prompt=none";

        MockHttpServletResponse ours = new MockHttpServletResponse();
        filterWithHandler().doFilter(authorizeRequest(query), ours, chain);
        MockHttpServletResponse sas = new MockHttpServletResponse();
        filterWithSasDefault().doFilter(authorizeRequest(query), sas, chain);

        assertThat(ours.getRedirectedUrl()).isEqualTo(sas.getRedirectedUrl()).doesNotContain("state=");
    }

    @Test
    @DisplayName("redirectableRequest: 코드 요청 예외 + redirect_uri 있을 때만")
    void redirectableRequest_onlyWhenSasKeptARedirectUri() {
        assertThat(AuthorizationEndpointErrorResponseHandler.redirectableRequest(
                rejection("invalid_scope", "scope", REDIRECT_URI, null))).isNotNull();
        assertThat(AuthorizationEndpointErrorResponseHandler.redirectableRequest(
                rejection("invalid_request", "client_id", null, null))).isNull();
        assertThat(AuthorizationEndpointErrorResponseHandler.redirectableRequest(
                rejection("invalid_request", "client_id", "", null))).isNull();
        assertThat(AuthorizationEndpointErrorResponseHandler.redirectableRequest(
                new OAuth2AuthorizationCodeRequestAuthenticationException(
                        new OAuth2Error("invalid_request"), null))).isNull();
        assertThat(AuthorizationEndpointErrorResponseHandler.redirectableRequest(
                new BadCredentialsException("x"))).isNull();
    }

    @Test
    @DisplayName("OAuth2 가 아닌 인증 예외 → 400 {invalid_request} (예외 메시지 안 실음; SAS 기본은 ClassCastException)")
    void nonOAuth2Exception_mapsToInvalidRequest() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new AuthorizationEndpointErrorResponseHandler().onAuthenticationFailure(
                authorizeRequest("response_type=code"), response, new BadCredentialsException("internal detail"));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getErrorMessage()).isNull();
        assertThat(response.getContentAsString()).isEqualTo("{\"error\":\"invalid_request\"}");
    }

    @Test
    @DisplayName("error_description 은 200자로 자른다")
    void longDescription_isBounded() throws Exception {
        String longText = "x".repeat(DirectOAuth2ErrorResponse.MAX_DESCRIPTION_LENGTH + 50);
        MockHttpServletResponse response = new MockHttpServletResponse();

        new AuthorizationEndpointErrorResponseHandler().onAuthenticationFailure(
                authorizeRequest("response_type=code"), response,
                new OAuth2AuthorizationCodeRequestAuthenticationException(
                        new OAuth2Error("invalid_request", longText, null), null));

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("error_description").asText()).hasSize(DirectOAuth2ErrorResponse.MAX_DESCRIPTION_LENGTH);
    }
}
