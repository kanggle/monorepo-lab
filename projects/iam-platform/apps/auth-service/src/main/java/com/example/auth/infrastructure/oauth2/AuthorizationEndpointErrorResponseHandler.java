package com.example.auth.infrastructure.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * TASK-BE-642 — the error response of the authorization endpoint ({@code /oauth2/authorize}).
 *
 * <p>SAS 1.4.1 decides, not this class, whether an error may go back to the client: its converter, provider and
 * validator attach a {@code redirect_uri} to the thrown {@link OAuth2AuthorizationCodeRequestAuthenticationException}
 * only when that URI can be trusted, and leave it empty otherwise (RFC 6749 § 4.1.2.1 — unknown or missing
 * {@code client_id}, an unregistered / mismatched / duplicated {@code redirect_uri}, and every request-parsing
 * error of the converter). Its default {@code OAuth2AuthorizationEndpointFilter.sendErrorResponse} then
 *
 * <ul>
 *   <li><b>redirects</b> to that URI with {@code error}, {@code error_description}, {@code error_uri} and
 *       {@code state} when it is set — kept here byte-for-byte ({@link #redirectToClient}, pinned against the SAS
 *       default by {@code AuthorizationEndpointErrorResponseHandlerTest});</li>
 *   <li>calls {@code sendError(400, …)} when it is not — and that is the defect: the container's {@code /error}
 *       dispatch matched only the {@code @Order(2)} chain, whose {@code anyRequest().denyAll()} answered the
 *       browser with <b>401 {@code Missing or invalid internal credentials}</b>
 *       ({@code V0034__add_console_vercel_domain_redirect_uri.sql:82-91}). This branch now writes
 *       {@code 400 application/json {"error", "error_description"}} itself ({@link DirectOAuth2ErrorResponse}),
 *       as {@link OidcLogoutErrorResponseHandler} does for logout. {@code /error} stays closed on Order 2.</li>
 * </ul>
 *
 * <p>The 400 body never repeats the request: {@code error_description} is SAS's fixed
 * {@code "OAuth 2.0 Parameter: <name>"}, bounded, and the rejected {@code client_id} / {@code redirect_uri} values
 * appear neither in the body nor in the INFO line.
 */
@Slf4j
public final class AuthorizationEndpointErrorResponseHandler implements AuthenticationFailureHandler {

    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        OAuth2AuthorizationCodeRequestAuthenticationToken redirectable = redirectableRequest(exception);
        if (redirectable != null) {
            redirectToClient(request, response, ((OAuth2AuthenticationException) exception).getError(), redirectable);
            return;
        }
        OAuth2Error error = DirectOAuth2ErrorResponse.toBoundedError(exception);
        log.info("Authorization request rejected without redirect: error={} description={}",
                error.getErrorCode(), error.getDescription());
        DirectOAuth2ErrorResponse.writeBadRequest(response, error);
    }

    /**
     * The request whose {@code redirect_uri} SAS kept — exactly the SAS default's test: an
     * {@link OAuth2AuthorizationCodeRequestAuthenticationException} with a request token whose redirect URI has
     * text. {@code null} means «do not redirect».
     */
    static OAuth2AuthorizationCodeRequestAuthenticationToken redirectableRequest(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthorizationCodeRequestAuthenticationException codeRequestException) {
            OAuth2AuthorizationCodeRequestAuthenticationToken token =
                    codeRequestException.getAuthorizationCodeRequestAuthentication();
            if (token != null && StringUtils.hasText(token.getRedirectUri())) {
                return token;
            }
        }
        return null;
    }

    /** SAS 1.4.1 {@code OAuth2AuthorizationEndpointFilter.sendErrorResponse}, redirect branch — unchanged. */
    private void redirectToClient(HttpServletRequest request, HttpServletResponse response, OAuth2Error error,
                                  OAuth2AuthorizationCodeRequestAuthenticationToken codeRequest) throws IOException {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(codeRequest.getRedirectUri())
                .queryParam(OAuth2ParameterNames.ERROR, error.getErrorCode());
        if (StringUtils.hasText(error.getDescription())) {
            uriBuilder.queryParam(OAuth2ParameterNames.ERROR_DESCRIPTION,
                    UriUtils.encode(error.getDescription(), StandardCharsets.UTF_8));
        }
        if (StringUtils.hasText(error.getUri())) {
            uriBuilder.queryParam(OAuth2ParameterNames.ERROR_URI,
                    UriUtils.encode(error.getUri(), StandardCharsets.UTF_8));
        }
        if (StringUtils.hasText(codeRequest.getState())) {
            uriBuilder.queryParam(OAuth2ParameterNames.STATE,
                    UriUtils.encode(codeRequest.getState(), StandardCharsets.UTF_8));
        }
        String redirectUri = uriBuilder.build(true).toUriString();
        redirectStrategy.sendRedirect(request, response, redirectUri);
    }
}
