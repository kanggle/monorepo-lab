package com.example.auth.infrastructure.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client {@link AuthenticationConverter} that authenticates a public PKCE client for
 * non-{@code authorization_code} requests — specifically the {@code refresh_token} grant
 * and {@code POST /oauth2/revoke} — when the client presents a {@code client_id} body
 * parameter and no client secret.
 *
 * <p><b>Why this exists</b>
 *
 * <p>SAS 1.4's stock {@code PublicClientAuthenticationConverter} only matches a PKCE
 * authorization-code token request — i.e. {@code grant_type=authorization_code +
 * code_verifier}. For any other request shape (refresh_token grant, /oauth2/revoke), it
 * returns {@code null}. The other client-auth converters require a client secret. The
 * net effect: a public PKCE SPA's refresh-token rotation and revocation flows fail with
 * {@code 401 invalid_client} before any grant-type provider runs.
 *
 * <p>This converter recognises that pattern and produces a {@link
 * PublicClientNonPkceAuthenticationToken} that is paired with {@link
 * PublicClientNonPkceAuthenticationProvider}. The provider validates the client and
 * marks it authenticated <em>without</em> calling {@code CodeVerifierAuthenticator},
 * which is the source of the regression that bit TASK-MONO-046-1 iter 5 (PKCE
 * wrong-code_verifier no longer rejected because a custom provider bypassed the
 * code_verifier check unconditionally).
 *
 * <p><b>Match conditions</b> (all must hold):
 * <ul>
 *   <li>HTTP method is {@code POST}</li>
 *   <li>No {@code Authorization} request header (avoids competing with
 *       {@code ClientSecretBasicAuthenticationConverter})</li>
 *   <li>Form parameter {@code client_id} is present and non-blank</li>
 *   <li>No {@code client_secret} form parameter (avoids competing with
 *       {@code ClientSecretPostAuthenticationConverter})</li>
 *   <li>EITHER {@code grant_type=refresh_token} (token endpoint) OR the request URI
 *       ends with {@code /oauth2/revoke}</li>
 *   <li>{@code code_verifier} is NOT present (so a PKCE auth-code request still goes to
 *       the stock converter)</li>
 * </ul>
 *
 * <p>The converter is registered via the {@code clientAuthentication().authenticationConverters()}
 * Consumer hook so it runs <em>before</em> the stock converters in the
 * {@link org.springframework.security.web.authentication.DelegatingAuthenticationConverter}
 * chain. First non-null wins; the stock converters take over for any request shape this
 * converter rejects.
 *
 * <p>TASK-MONO-046-7 Cluster A — root-cause fix for "public PKCE client cannot use
 * refresh_token grant or /oauth2/revoke" without regressing PKCE wrong-code_verifier.
 */
public final class PublicClientNonPkceAuthenticationConverter implements AuthenticationConverter {

    private static final String REVOKE_PATH = "/oauth2/revoke";

    @Override
    public Authentication convert(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return null;
        }
        // Skip if Basic auth header is present — that's the secret_basic flow.
        if (StringUtils.hasText(request.getHeader("Authorization"))) {
            return null;
        }

        MultiValueMap<String, String> parameters = readFormParameters(request);

        // Skip if the request carries a code_verifier — that's a PKCE auth-code request
        // and must be handled by the stock PublicClientAuthenticationConverter so the
        // CodeVerifierAuthenticator runs and validates the code_verifier (regression
        // guard against TASK-MONO-046-7 Edge Case #1).
        if (StringUtils.hasText(parameters.getFirst("code_verifier"))) {
            return null;
        }

        String clientId = parameters.getFirst("client_id");
        if (!StringUtils.hasText(clientId)) {
            return null;
        }

        // Skip if client_secret is present — that's the secret_post flow.
        if (StringUtils.hasText(parameters.getFirst("client_secret"))) {
            return null;
        }

        // Multiple values for client_id is invalid per RFC 6749 § 5.2.
        List<String> clientIdValues = parameters.get("client_id");
        if (clientIdValues != null && clientIdValues.size() > 1) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
        }

        boolean isRefreshTokenGrant = AuthorizationGrantType.REFRESH_TOKEN.getValue()
                .equals(parameters.getFirst("grant_type"));
        boolean isRevokeEndpoint = isRevokeRequest(request);

        if (!isRefreshTokenGrant && !isRevokeEndpoint) {
            return null;
        }

        // Capture additional parameters for downstream grant-type / revocation
        // providers. Mirror the stock converter behaviour: drop client_id from the
        // additional-parameter map so it doesn't appear twice.
        Map<String, Object> additionalParameters = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : parameters.entrySet()) {
            if ("client_id".equals(entry.getKey())) {
                continue;
            }
            List<String> values = entry.getValue();
            additionalParameters.put(entry.getKey(),
                    values.size() == 1 ? values.get(0) : values.toArray(new String[0]));
        }

        return new PublicClientNonPkceAuthenticationToken(clientId, additionalParameters);
    }

    private static boolean isRevokeRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && uri.endsWith(REVOKE_PATH);
    }

    /**
     * Extracts form parameters defensively. Avoids depending on the SAS internal
     * {@code OAuth2EndpointUtils.getFormParameters} (package-private in some 1.x
     * versions) — uses the servlet's parameter map which has already been parsed.
     */
    private static MultiValueMap<String, String> readFormParameters(HttpServletRequest request) {
        org.springframework.util.LinkedMultiValueMap<String, String> result =
                new org.springframework.util.LinkedMultiValueMap<>();
        Map<String, String[]> map = request.getParameterMap();
        Iterator<Map.Entry<String, String[]>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String[]> entry = it.next();
            for (String value : entry.getValue()) {
                result.add(entry.getKey(), value);
            }
        }
        return result;
    }

    /**
     * Marker subtype emitted by {@link PublicClientNonPkceAuthenticationConverter} so
     * {@link PublicClientNonPkceAuthenticationProvider} can match precisely without
     * also short-circuiting the stock {@code PublicClientAuthenticationProvider} for
     * PKCE auth-code requests.
     */
    public static final class PublicClientNonPkceAuthenticationToken
            extends OAuth2ClientAuthenticationToken {

        PublicClientNonPkceAuthenticationToken(String clientId, Map<String, Object> additionalParameters) {
            super(clientId, ClientAuthenticationMethod.NONE, /*credentials*/ null,
                    additionalParameters == null ? new HashMap<>() : additionalParameters);
        }

        PublicClientNonPkceAuthenticationToken(
                org.springframework.security.oauth2.server.authorization.client.RegisteredClient registeredClient) {
            super(registeredClient, ClientAuthenticationMethod.NONE, /*credentials*/ null);
        }
    }
}
