package com.example.auth.infrastructure.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationConverter;

/**
 * Identifies a public PKCE client by {@code client_id} alone for the
 * {@code refresh_token} grant.
 *
 * <p>SAS 1.4's stock {@link org.springframework.security.oauth2.server.authorization.web.authentication.PublicClientAuthenticationConverter}
 * only matches PKCE token requests — i.e. {@code grant_type=authorization_code}
 * with a {@code code_verifier} parameter. For the {@code refresh_token} grant,
 * SAS expects the client to be authenticated by a different converter (Basic auth,
 * client_secret_post, JWT client assertion, ...). Public PKCE clients have none of
 * those credentials, so when they call {@code POST /oauth2/token} with
 * {@code grant_type=refresh_token} the {@link org.springframework.security.oauth2.server.authorization.web.OAuth2ClientAuthenticationFilter}
 * authenticates no client and {@link org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationProvider}
 * (or our {@link SasRefreshTokenAuthenticationProvider}) throws
 * {@code invalid_client} → 401.
 *
 * <p>This converter fills the gap. It runs in the SAS client-authentication
 * converter chain and, for {@code grant_type=refresh_token} with a single
 * {@code client_id} parameter, returns an
 * {@link OAuth2ClientAuthenticationToken} carrying the client_id and method=NONE.
 * The downstream {@link PublicClientRefreshTokenAuthenticationProvider} validates
 * the client exists and supports method=NONE, then issues an authenticated token.
 *
 * <p>TASK-MONO-046-1 Cluster A — required for SPA refresh-token rotation.
 */
@Slf4j
public class PublicClientRefreshTokenAuthenticationConverter implements AuthenticationConverter {

    private static final String GRANT_TYPE_PARAM = "grant_type";
    private static final String CLIENT_ID_PARAM = "client_id";

    @Override
    public Authentication convert(HttpServletRequest request) {
        String grantType = request.getParameter(GRANT_TYPE_PARAM);
        if (!AuthorizationGrantType.REFRESH_TOKEN.getValue().equals(grantType)) {
            return null;
        }

        String[] clientIdValues = request.getParameterValues(CLIENT_ID_PARAM);
        if (clientIdValues == null || clientIdValues.length != 1) {
            return null;
        }
        String clientId = clientIdValues[0];
        if (clientId == null || clientId.isBlank()) {
            return null;
        }

        // If a credential-based converter (Basic, post, JWT assertion) has already
        // populated SecurityContextHolder we should not override it. The SAS
        // OAuth2ClientAuthenticationFilter passes the request through every
        // converter, returning the first non-null Authentication. By placing this
        // converter LAST in the chain (see AuthorizationServerConfig), we reach
        // here only when no other converter authenticated the request.
        return new OAuth2ClientAuthenticationToken(
                clientId, ClientAuthenticationMethod.NONE, null, null);
    }
}
