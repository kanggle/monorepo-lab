package com.example.auth.infrastructure.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationConverter;

/**
 * TASK-BE-327 (ADR-MONO-020 § 3.3 step 2, D2) — SAS token-endpoint converter for
 * the assume-tenant RFC 8693 token-exchange grant. Registered via
 * {@code tokenEndpoint().accessTokenRequestConverter(...)}; returns {@code null}
 * for any other {@code grant_type} so {@code authorization_code} /
 * {@code client_credentials} / {@code refresh_token} are byte-unchanged.
 *
 * <p>On a token-exchange request it parses {@code subject_token},
 * {@code subject_token_type}, and the selected tenant ({@code audience}) from the
 * form body, reads the already-authenticated client principal from the
 * {@link SecurityContextHolder} (the client-auth chain ran first), and produces
 * an {@link AssumeTenantAuthenticationToken} for
 * {@link AssumeTenantAuthenticationProvider}.
 *
 * <p>Validation here is protocol-level only (RFC 8693
 * {@code invalid_request}): missing {@code subject_token} /
 * {@code subject_token_type} / {@code audience} → {@code invalid_request} (no
 * admin call, no mint). Subject-token validity + the assignment gate are the
 * provider's responsibility ({@code invalid_grant}).
 */
public final class AssumeTenantAuthenticationConverter implements AuthenticationConverter {

    /** RFC 8693 parameter names (not all present in OAuth2ParameterNames across SAS versions). */
    private static final String PARAM_SUBJECT_TOKEN = "subject_token";
    private static final String PARAM_SUBJECT_TOKEN_TYPE = "subject_token_type";

    @Override
    public Authentication convert(HttpServletRequest request) {
        String grantType = request.getParameter(OAuth2ParameterNames.GRANT_TYPE);
        if (!AuthorizationGrantType.TOKEN_EXCHANGE.getValue().equals(grantType)) {
            // Not our grant — let the stock converters handle it (existing flows untouched).
            return null;
        }

        String subjectToken = request.getParameter(PARAM_SUBJECT_TOKEN);
        if (subjectToken == null || subjectToken.isBlank()) {
            throw protocolError("subject_token is required");
        }
        String subjectTokenType = request.getParameter(PARAM_SUBJECT_TOKEN_TYPE);
        if (subjectTokenType == null || subjectTokenType.isBlank()) {
            throw protocolError("subject_token_type is required");
        }
        // Selected tenant carried as the RFC 8693 'audience' parameter (auth-api.md).
        String selectedTenantId = request.getParameter(OAuth2ParameterNames.AUDIENCE);
        if (selectedTenantId == null || selectedTenantId.isBlank()) {
            throw protocolError("audience (selected tenant) is required");
        }

        Authentication clientPrincipal = SecurityContextHolder.getContext().getAuthentication();

        return new AssumeTenantAuthenticationToken(
                clientPrincipal, subjectToken, subjectTokenType, selectedTenantId.trim());
    }

    private static OAuth2AuthenticationException protocolError(String description) {
        return new OAuth2AuthenticationException(
                new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST, description, null));
    }
}
