package com.example.auth.infrastructure.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import java.io.IOException;

/**
 * TASK-BE-641 — the error response of RP-initiated logout ({@code /connect/logout}).
 *
 * <p>Writes {@code 400 application/json {"error": …, "error_description": …}} <b>directly</b>
 * (auth-api.md § GET /connect/logout). SAS 1.4.1's default ({@code OidcLogoutEndpointFilter.sendErrorResponse})
 * calls {@code sendError(400, …)} with no body; the container then dispatches to {@code /error}, which only
 * the {@code @Order(2)} chain matches, and its {@code anyRequest().denyAll()} answered the browser with
 * <b>401 {@code Missing or invalid internal credentials}</b>. Writing the body here keeps the container's error
 * path off the route — the owner's decision D1 (a): {@code /error} and {@code /connect/**} stay closed on Order 2.
 *
 * <p>Body and bounds: {@link DirectOAuth2ErrorResponse} (shared with the authorization endpoint's handler since
 * TASK-BE-642) — e.g. {@code "OpenID Connect 1.0 Logout Request Parameter: id_token_hint"}, no {@code error_uri}.
 *
 * <p>One INFO line per rejection, code + description only (no token, no query) — until now these rejections
 * left no line at all (SAS logs them at TRACE), so the inner cause of the owner's case was invisible.
 */
@Slf4j
public final class OidcLogoutErrorResponseHandler implements AuthenticationFailureHandler {

    static final int MAX_DESCRIPTION_LENGTH = DirectOAuth2ErrorResponse.MAX_DESCRIPTION_LENGTH;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        OAuth2Error error = toBoundedError(exception);
        log.info("RP-initiated logout rejected: error={} description={}",
                error.getErrorCode(), error.getDescription());
        DirectOAuth2ErrorResponse.writeBadRequest(response, error);
    }

    static OAuth2Error toBoundedError(AuthenticationException exception) {
        // The SAS filter routes only OAuth2AuthenticationException here; anything else is still a refused
        // logout request, never an internal-credential failure.
        return DirectOAuth2ErrorResponse.toBoundedError(exception);
    }
}
