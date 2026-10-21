package com.example.auth.infrastructure.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.http.converter.OAuth2ErrorHttpMessageConverter;
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
 * <p>Body: the OAuth 2.0 error members only. {@code error_uri} is dropped (the contract names exactly two fields)
 * and {@code error_description} — a fixed SAS sentence such as
 * {@code "OpenID Connect 1.0 Logout Request Parameter: id_token_hint"}, never a request value — is bounded to
 * {@value #MAX_DESCRIPTION_LENGTH} characters anyway. Serialised by Spring Security's
 * {@link OAuth2ErrorHttpMessageConverter}, the same writer SAS uses for its token-endpoint errors.
 *
 * <p>One INFO line per rejection, code + description only (no token, no query) — until now these rejections
 * left no line at all (SAS logs them at TRACE), so the inner cause of the owner's case was invisible.
 */
@Slf4j
public final class OidcLogoutErrorResponseHandler implements AuthenticationFailureHandler {

    static final int MAX_DESCRIPTION_LENGTH = 200;

    private final HttpMessageConverter<OAuth2Error> errorConverter = new OAuth2ErrorHttpMessageConverter();

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        OAuth2Error error = toBoundedError(exception);
        log.info("RP-initiated logout rejected: error={} description={}",
                error.getErrorCode(), error.getDescription());
        if (response.isCommitted()) {
            return;
        }
        ServletServerHttpResponse httpResponse = new ServletServerHttpResponse(response);
        httpResponse.setStatusCode(HttpStatus.BAD_REQUEST);
        httpResponse.getHeaders().setCacheControl("no-store");
        errorConverter.write(error, MediaType.APPLICATION_JSON, httpResponse);
    }

    static OAuth2Error toBoundedError(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oauth2) {
            OAuth2Error source = oauth2.getError();
            return new OAuth2Error(source.getErrorCode(), bound(source.getDescription()), null);
        }
        // The SAS filter routes only OAuth2AuthenticationException here; anything else is still a refused
        // logout request, never an internal-credential failure.
        return new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST, null, null);
    }

    private static String bound(String description) {
        if (description == null || description.length() <= MAX_DESCRIPTION_LENGTH) {
            return description;
        }
        return description.substring(0, MAX_DESCRIPTION_LENGTH);
    }
}
