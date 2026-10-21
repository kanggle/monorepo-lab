package com.example.auth.infrastructure.oauth2;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.http.converter.OAuth2ErrorHttpMessageConverter;

import java.io.IOException;

/**
 * TASK-BE-641 / TASK-BE-642 — writes an SAS endpoint's {@code 400 application/json {"error", "error_description"}}
 * <b>directly</b>, instead of the SAS default {@code sendError(400, …)}.
 *
 * <p>Why directly: {@code sendError} makes the container dispatch to {@code /error}; no chain except the
 * {@code @Order(2)} one matches that dispatch, and its {@code anyRequest().denyAll()} answers the browser with
 * <b>401 {@code Missing or invalid internal credentials}</b>. {@code /error} stays closed on Order 2
 * ({@code TASK-MONO-801} rejected opening it), so each SAS endpoint whose default error path is
 * {@code sendError} gets a handler that uses this writer (auth-api.md § GET /connect/logout ·
 * § GET /oauth2/authorize).
 *
 * <p>Body: the OAuth 2.0 error members only. {@code error_uri} is dropped (the contracts name exactly two
 * fields) and {@code error_description} — a fixed SAS sentence such as {@code "OAuth 2.0 Parameter: client_id"},
 * never a request value — is bounded to {@value #MAX_DESCRIPTION_LENGTH} characters anyway. Serialised by Spring
 * Security's {@link OAuth2ErrorHttpMessageConverter}, the same writer SAS uses for its token-endpoint errors.
 */
final class DirectOAuth2ErrorResponse {

    static final int MAX_DESCRIPTION_LENGTH = 200;

    // Stateless per write (it delegates to a Jackson converter), so one instance serves every request.
    private static final HttpMessageConverter<OAuth2Error> ERROR_CONVERTER = new OAuth2ErrorHttpMessageConverter();

    private DirectOAuth2ErrorResponse() {
    }

    /**
     * The error to show: the OAuth2 code + bounded description, no {@code error_uri}. Anything that is not an
     * {@link OAuth2AuthenticationException} is still a refused request, never an internal-credential failure —
     * {@code invalid_request} without a description (the exception message is not shown).
     */
    static OAuth2Error toBoundedError(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oauth2) {
            OAuth2Error source = oauth2.getError();
            return new OAuth2Error(source.getErrorCode(), bound(source.getDescription()), null);
        }
        return new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST, null, null);
    }

    /** {@code 400}, {@code Cache-Control: no-store}, the JSON body — no {@code sendError}, so no {@code /error}. */
    static void writeBadRequest(HttpServletResponse response, OAuth2Error error) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        ServletServerHttpResponse httpResponse = new ServletServerHttpResponse(response);
        httpResponse.setStatusCode(HttpStatus.BAD_REQUEST);
        httpResponse.getHeaders().setCacheControl("no-store");
        ERROR_CONVERTER.write(error, MediaType.APPLICATION_JSON, httpResponse);
    }

    private static String bound(String description) {
        if (description == null || description.length() <= MAX_DESCRIPTION_LENGTH) {
            return description;
        }
        return description.substring(0, MAX_DESCRIPTION_LENGTH);
    }
}
